package fr.ayoub.pvp.core.lobby;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.menu.PartyMenu;
import fr.ayoub.pvp.core.menu.PlayMenu;
import fr.ayoub.pvp.core.menu.ProfileMenu;
import fr.ayoub.pvp.core.menu.SpectateMenu;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.core.ui.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Everything that happens to a player while they are in the lobby. */
public final class LobbyListener implements Listener {

    /** How far below the lobby a player must fall before we rescue them. */
    private static final int VOID_DEPTH = 10;

    private final PvPEnginePlugin plugin;
    private final LobbyService lobby;
    private final HotbarItems hotbar;

    public LobbyListener(PvPEnginePlugin plugin, LobbyService lobby, HotbarItems hotbar) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.hotbar = hotbar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        lobby.send(player);
        Sidebar.update(plugin, player);

        // Make sure the player exists in the database (off the main thread).
        plugin.async().execute(() -> plugin.players().upsert(player.getUniqueId(), player.getName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Leaving the party also pulls the party's ticket out of the queue — the group
        // that was queued no longer exists.
        plugin.parties().leave(event.getPlayer());
        plugin.queue().leave(event.getPlayer());
        Sidebar.clear(event.getPlayer());
    }

    /** Right-clicking a hotbar item does everything. No commands, ever. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        hotbar.actionOf(event.getItem()).ifPresent(hotbarAction -> {
            event.setCancelled(true);

            switch (hotbarAction) {
                case HotbarItems.ACTION_PLAY -> new PlayMenu(plugin).open(player);
                case HotbarItems.ACTION_PARTY -> new PartyMenu(plugin).open(player);
                case HotbarItems.ACTION_PROFILE -> new ProfileMenu(plugin).open(player);
                case HotbarItems.ACTION_SPECTATE -> new SpectateMenu(plugin).open(player);
                case HotbarItems.ACTION_LEAVE_QUEUE -> {
                    plugin.queue().leave(player);
                    player.getInventory().setItem(HotbarItems.SLOT_LEAVE_QUEUE, null);
                    player.sendMessage(Component.text("You left the queue.", NamedTextColor.YELLOW));
                    Sidebar.update(plugin, player);
                }
                default -> {
                    // unknown action — ignore
                }
            }
        });
    }

    /**
     * The void catch.
     *
     * The barrier wall around the lobby is the real protection, but the client can be
     * lied to (or the map edited). If a lobby player ends up well below the platform,
     * put them back — falling forever is the worst possible bug.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!lobby.isInLobby(player)) {
            return;
        }
        // A spectator is flying around an arena, far below the lobby platform. They are
        // not falling into the void — rescuing them would teleport them straight out of
        // the match they are watching.
        if (plugin.matches().isSpectating(player)) {
            return;
        }
        if (event.getTo().getY() < lobby.spawn().getY() - VOID_DEPTH) {
            lobby.send(player);
        }
    }

    /**
     * Spectators have no usable hotbar (SPECTATOR mode ignores item interactions), so the
     * way out is to sneak. It is also what every other server does, so players expect it.
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && plugin.matches().isSpectating(event.getPlayer())) {
            plugin.matches().stopSpectating(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (lobby.isInLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            return;   // menus handle their own clicks
        }
        if (event.getWhoClicked() instanceof Player player && lobby.isInLobby(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && lobby.isInLobby(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && lobby.isInLobby(player)) {
            event.setCancelled(true);
        }
    }
}

package fr.ayoub.pvp.core.lobby;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.menu.Menus;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.core.ui.Sidebar;
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

        // A match may still be holding their place. Reconnecting inside the grace period puts
        // them straight back in it — the lobby does not get to grab them on the way past and
        // teleport them out of a game they are still in.
        if (plugin.matches().tryRejoin(player)) {
            Sidebar.update(plugin, player);
            plugin.async().execute(() ->
                    plugin.players().upsert(player.getUniqueId(), player.getName()));
            return;
        }

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
        if (plugin.matches().isInMatch(player) || plugin.matches().isSpectating(player)) {
            return;   // the lobby hotbar is not theirs to use right now
        }

        hotbar.actionOf(event.getItem()).ifPresent(hotbarAction -> {
            event.setCancelled(true);
            Menus.open(plugin, player, hotbarAction);
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
     * The way out of spectator mode.
     *
     * Sneak is the only key a spectator can send us — SPECTATOR ignores item use, dropping
     * and hand-swapping. But sneak is also how a spectator flies <b>down</b>, so leaving
     * has to be a <b>double</b>-sneak: a single one must keep working as "descend".
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            plugin.matches().handleSpectatorSneak(event.getPlayer());
        }
    }

    /**
     * A camera-locked spectator pressed sneak, which Minecraft reads as "let go of this player".
     *
     * Letting go is the free-fly wallhack the lock exists to prevent, so we never allow it: the
     * event is cancelled, and the press is turned into "watch the next player" (or, on a
     * double-tap, "leave") by the match. For a roaming spectator this event never fires, and this
     * handler leaves them to their normal descend-sneak.
     */
    @EventHandler
    public void onStopSpectating(com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent event) {
        if (plugin.matches().handleSpectatorDetach(event.getPlayer())) {
            event.setCancelled(true);
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

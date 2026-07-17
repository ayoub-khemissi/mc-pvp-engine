package fr.ayoub.pvp.core.admin;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.arena.ArenaLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defining an arena with items instead of commands.
 *
 * <p>{@code /pvpadmin map edit <id>} drops the admin into an editor: their inventory is set aside and
 * replaced with a row of tools. Right-click a tool to place its marker where you stand — right-click
 * again on a marker you placed to take it back. A spawn is centred on the block and its look snapped
 * to a straight direction, so nobody has to eyeball 47°. The whole layout is drawn in the air with
 * particles — red for team 0, blue for team 1, green for the bounds — so you see it as you build it.
 *
 * <p>The two corners are the opposite extremes of the <b>whole</b> arena: one low, one flown up high,
 * enclosing everything. That box is both the invisible wall and the reset volume.
 */
public final class MapEditor implements Listener {

    private final PvPEnginePlugin plugin;
    private final NamespacedKey toolKey;

    private final Map<UUID, Session> sessions = new HashMap<>();

    private record Session(MapDraft draft, ItemStack[] inventory, GameMode gameMode,
                           boolean flying, BukkitTask task) {
    }

    public MapEditor(PvPEnginePlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "maptool");
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // --- entering / leaving ---------------------------------------------------------------

    public void start(Player player, String id) {
        if (isEditing(player)) {
            finish(player, false);
        }

        MapDraft draft = new MapDraft(id, player.getWorld().getName());

        ItemStack[] saved = player.getInventory().getContents().clone();
        GameMode mode = player.getGameMode();
        boolean flying = player.isFlying();

        player.getInventory().clear();
        giveTools(player);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, () -> draw(player, draft), 0L, 10L);

        sessions.put(player.getUniqueId(), new Session(draft, saved, mode, flying, task));

        tell(player, "Editing '" + id + "'. Right-click a tool to place a marker; click it again on "
                + "the marker to remove it.", NamedTextColor.GOLD);
        tell(player, "Corners are the two opposite extremes of the WHOLE arena — one low, one high "
                + "above the roof.", NamedTextColor.GRAY);
    }

    public void finish(Player player, boolean save) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.task().cancel();

        if (save && !doSave(player, session.draft())) {
            // Not saved (something missing). Put them straight back into the session.
            sessions.put(player.getUniqueId(), new Session(session.draft(), session.inventory(),
                    session.gameMode(), session.flying(),
                    plugin.getServer().getScheduler().runTaskTimer(
                            plugin, () -> draw(player, session.draft()), 0L, 10L)));
            return;
        }

        player.getInventory().setContents(session.inventory());
        player.setGameMode(session.gameMode());
        player.setFlying(session.flying() && player.getAllowFlight());
    }

    private boolean doSave(Player player, MapDraft draft) {
        if (!draft.isReady()) {
            tell(player, "Not yet — still need: " + String.join(", ", draft.missing()),
                    NamedTextColor.RED);
            return false;
        }
        for (String warning : draft.warnings()) {
            tell(player, "Warning: " + warning, NamedTextColor.YELLOW);
        }
        try {
            draft.save(new java.io.File(plugin.getDataFolder(), "maps"));
        } catch (IOException e) {
            tell(player, "Could not write the map file: " + e.getMessage(), NamedTextColor.RED);
            return false;
        }

        plugin.arenas().load(ArenaLoader.loadAll(plugin));
        plugin.resets().prepare(plugin.arenas().all());
        tell(player, "Saved '" + draft.id() + "' and loaded it. Queue a duel to test it.",
                NamedTextColor.GREEN);
        return true;
    }

    // --- the tools ------------------------------------------------------------------------

    private enum Tool {
        SPAWN0("spawn0", org.bukkit.Material.RED_BED, "Spawn — Team 0 (red)"),
        SPAWN1("spawn1", org.bukkit.Material.BLUE_BED, "Spawn — Team 1 (blue)"),
        CORNER1("corner1", org.bukkit.Material.LIME_CONCRETE, "Corner 1 (a low corner)"),
        CORNER2("corner2", org.bukkit.Material.GREEN_CONCRETE, "Corner 2 (high, opposite)"),
        RESET("reset", org.bukkit.Material.BARRIER, "Reset — clear everything"),
        SAVE("save", org.bukkit.Material.EMERALD_BLOCK, "Save & load"),
        CANCEL("cancel", org.bukkit.Material.RED_CONCRETE, "Cancel — discard");

        final String id;
        final org.bukkit.Material material;
        final String label;

        Tool(String id, org.bukkit.Material material, String label) {
            this.id = id;
            this.material = material;
            this.label = label;
        }
    }

    private void giveTools(Player player) {
        int[] slots = {0, 1, 3, 4, 6, 7, 8};
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            player.getInventory().setItem(slots[i], toolItem(tools[i]));
        }
    }

    private ItemStack toolItem(Tool tool) {
        ItemStack item = new ItemStack(tool.material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(tool.label, NamedTextColor.WHITE)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.id);
        });
        return item;
    }

    private Tool toolOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(toolKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (Tool tool : Tool.values()) {
            if (tool.id.equals(id)) {
                return tool;
            }
        }
        return null;
    }

    // --- events ---------------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isEditing(event.getPlayer())) {
            return;
        }
        Tool tool = toolOf(event.getItem());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);   // never place the bed/concrete as a real block

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        MapDraft draft = sessions.get(player.getUniqueId()).draft();
        Location at = player.getLocation();

        switch (tool) {
            case SPAWN0 -> {
                int n = draft.toggleSpawn(0, at);
                tell(player, n < 0 ? "Removed a team-0 spawn (" + -n + " left)."
                        : "Team-0 spawn #" + n + ", facing " + facing(at) + ".", NamedTextColor.RED);
            }
            case SPAWN1 -> {
                int n = draft.toggleSpawn(1, at);
                tell(player, n < 0 ? "Removed a team-1 spawn (" + -n + " left)."
                        : "Team-1 spawn #" + n + ", facing " + facing(at) + ".", NamedTextColor.BLUE);
            }
            case CORNER1 -> {
                draft.toggleCorner(1, at);
                tell(player, "Corner 1 " + (draft.corner(1) != null ? "set" : "cleared") + ". "
                        + draft.status(), NamedTextColor.GREEN);
            }
            case CORNER2 -> {
                draft.toggleCorner(2, at);
                tell(player, "Corner 2 " + (draft.corner(2) != null ? "set" : "cleared") + ". "
                        + draft.status(), NamedTextColor.GREEN);
            }
            case RESET -> {
                draft.clearAll();
                tell(player, "Cleared. Start again.", NamedTextColor.YELLOW);
            }
            case SAVE -> finish(player, true);
            case CANCEL -> {
                finish(player, false);
                tell(player, "Discarded.", NamedTextColor.YELLOW);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isEditing(event.getPlayer()) && toolOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isEditing(player)
                && toolOf(event.getCurrentItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Their inventory is restored on save/cancel; a disconnect must not lose it. Give it back
        // and drop the session — a half-defined map is not worth stranding an inventory over.
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.task().cancel();
            event.getPlayer().getInventory().setContents(session.inventory());
            event.getPlayer().setGameMode(session.gameMode());
        }
    }

    // --- the picture ----------------------------------------------------------------------

    private void draw(Player player, MapDraft draft) {
        for (Location spawn : draft.spawns(0)) {
            spawnMarker(player, spawn, Color.RED);
        }
        for (Location spawn : draft.spawns(1)) {
            spawnMarker(player, spawn, Color.BLUE);
        }
        box(player, draft.corner(1), draft.corner(2));
        cornerMarker(player, draft.corner(1));
        cornerMarker(player, draft.corner(2));
    }

    private void spawnMarker(Player player, Location at, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        for (double y = 0; y <= 2.2; y += 0.3) {
            player.spawnParticle(Particle.DUST, at.getX(), at.getY() + y, at.getZ(), 1, dust);
        }
        // A short arrow in the facing direction, so you can see which way they look.
        double rad = Math.toRadians(at.getYaw());
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        for (double d = 0.4; d <= 1.6; d += 0.4) {
            player.spawnParticle(Particle.DUST, at.getX() + dx * d, at.getY() + 1.0,
                    at.getZ() + dz * d, 1, dust);
        }
    }

    private void cornerMarker(Player player, Location at) {
        if (at == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.6f);
        player.spawnParticle(Particle.DUST, at.getBlockX() + 0.5, at.getBlockY() + 0.5,
                at.getBlockZ() + 0.5, 4, 0.2, 0.2, 0.2, dust);
    }

    /** The twelve edges of the bounds box, sampled so you can see the whole cage. */
    private void box(Player player, Location a, Location b) {
        if (a == null || b == null) {
            return;
        }
        double x1 = Math.min(a.getBlockX(), b.getBlockX());
        double y1 = Math.min(a.getBlockY(), b.getBlockY());
        double z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        double x2 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double y2 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        double z2 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 120), 1.2f);
        double step = 2.0;
        for (double x = x1; x <= x2; x += step) {
            edge(player, x, y1, z1, dust); edge(player, x, y1, z2, dust);
            edge(player, x, y2, z1, dust); edge(player, x, y2, z2, dust);
        }
        for (double y = y1; y <= y2; y += step) {
            edge(player, x1, y, z1, dust); edge(player, x1, y, z2, dust);
            edge(player, x2, y, z1, dust); edge(player, x2, y, z2, dust);
        }
        for (double z = z1; z <= z2; z += step) {
            edge(player, x1, y1, z, dust); edge(player, x2, y1, z, dust);
            edge(player, x1, y2, z, dust); edge(player, x2, y2, z, dust);
        }
    }

    private void edge(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, dust);
    }

    private static String facing(Location at) {
        return switch ((int) fr.ayoub.pvp.domain.arena.CardinalFacing.snap(at.getYaw())) {
            case 0 -> "south";
            case 90 -> "west";
            case 180 -> "north";
            default -> "east";
        };
    }

    private void tell(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }
}

package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.BuildRules;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.menu.ExitMenu;
import fr.ayoub.pvp.mode.fortress.menu.PaletteMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * The rules of a build zone, enforced as they are broken rather than at save time.
 *
 * A builder is in <b>creative</b>, so they can conjure any block in the game. The palette is
 * therefore not enforced by what they hold — it is enforced <b>where they place it</b>. That
 * is the only check that cannot be got around, and it is the same check the validator runs
 * before a fortress is ever pasted into a match.
 */
public final class BuildListener implements Listener {

    private final BuildZoneService zones;
    private final FortressConfig config;

    public BuildListener(BuildZoneService zones, FortressConfig config) {
        this.zones = zones;
        this.config = config;
    }

    // --- placing ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BuildSession session = zones.sessionOf(player).orElse(null);
        if (session == null) {
            // Nobody builds in the build world without a session — not even an operator
            // who flew in by accident.
            if (zones.world() != null && zones.world().equals(event.getBlock().getWorld())) {
                event.setCancelled(true);
            }
            return;
        }

        Block block = event.getBlock();
        BuildZone zone = session.zone();
        BuildRules rules = config.buildRules();

        if (!zone.isInCube(block)) {
            refuse(event, player, "You can only build inside the fortress cube.");
            return;
        }

        String id = block.getType().name();
        if (!rules.allows(id)) {
            refuse(event, player, id + " is not allowed in a fortress.");
            return;
        }

        Blueprint blueprint = session.blueprint();
        if (rules.remaining(blueprint, id) <= 0) {
            refuse(event, player, "No " + id + " left — the limit is " + rules.quota(id) + ".");
            return;
        }

        BlockPos pos = zone.toBlueprint(block);
        if (inCrystalClearance(blueprint, rules, pos)) {
            refuse(event, player, "The End Crystal needs this space to stay open.");
            return;
        }

        blueprint.set(pos, id);
        session.touch();
        showBudget(player, rules, blueprint, id);
    }

    /** The pocket around the crystal is refused as it is filled, not discovered at save. */
    private static boolean inCrystalClearance(Blueprint blueprint, BuildRules rules, BlockPos pos) {
        BlockPos crystal = blueprint.crystal();
        if (crystal == null) {
            return false;
        }

        int dy = pos.y() - crystal.y();
        if (dy < 0 || dy >= rules.clearanceHeight()) {
            return false;
        }
        return Math.abs(pos.x() - crystal.x()) <= rules.clearanceRadius()
                && Math.abs(pos.z() - crystal.z()) <= rules.clearanceRadius();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BuildSession session = zones.sessionOf(player).orElse(null);

        if (session == null) {
            if (zones.world() != null && zones.world().equals(event.getBlock().getWorld())) {
                event.setCancelled(true);
            }
            return;
        }

        Block block = event.getBlock();
        BuildZone zone = session.zone();

        if (BuildRoom.isFurniture(zone, block) || !zone.isInCube(block)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Only the cube can be edited.", NamedTextColor.RED));
            return;
        }

        session.blueprint().set(zone.toBlueprint(block), Blueprint.AIR);
        session.touch();
    }

    // --- the crystal ---------------------------------------------------------------

    /**
     * The End Crystal is an entity, and vanilla already refuses to place it anywhere but
     * on obsidian or bedrock — which happens to be the rule we want. We only add ours: it
     * goes in the cube, there is exactly one, and it keeps its air pocket.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlaceCrystal(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) {
            return;
        }
        Player player = event.getPlayer();
        BuildSession session = player == null ? null : zones.sessionOf(player).orElse(null);

        if (session == null) {
            if (zones.world() != null && zones.world().equals(event.getBlock().getWorld())) {
                event.setCancelled(true);
            }
            return;
        }

        BuildZone zone = session.zone();
        Block block = event.getBlock().getRelative(0, 1, 0);   // the crystal stands above it

        if (!zone.isInCube(block)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("The End Crystal must be inside the cube.",
                    NamedTextColor.RED));
            return;
        }

        BlockPos pos = zone.toBlueprint(block);
        Blueprint blueprint = session.blueprint();

        if (!blocksAreClearAround(blueprint, pos)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "The End Crystal needs a clear pocket around it.", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);   // we spawn it ourselves, so there is only ever one
        blueprint.crystal(pos);
        zones.spawnCrystal(zone, pos);
        session.touch();

        player.sendActionBar(Component.text("End Crystal placed.", NamedTextColor.LIGHT_PURPLE));
    }

    private boolean blocksAreClearAround(Blueprint blueprint, BlockPos crystal) {
        BuildRules rules = config.buildRules();
        int radius = rules.clearanceRadius();

        for (int dy = 0; dy < rules.clearanceHeight(); dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = crystal.offset(dx, dy, dz);
                    if (blueprint.contains(pos) && !Blueprint.AIR.equals(blueprint.get(pos))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * A bucket does not fire a place event.
     *
     * So lava and water slipped through every check: they were placed, they were not in the
     * palette, and they simply vanished at save — silently, with no message. Refuse them
     * outright. The day fluids become part of a fortress, this is where they come back.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (zones.isBuilding(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "Fluids cannot be part of a fortress.", NamedTextColor.RED));
        }
    }

    /**
     * The End Crystal is the only entity a fortress is made of.
     *
     * An armour stand, a boat, a painting — none of them are blocks, so none of them would
     * be saved. Placing one would look like it worked and then be gone. Refuse them here,
     * where the player can be told why.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlaceEntity(org.bukkit.event.entity.EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal || event.getPlayer() == null) {
            return;   // the crystal has its own handler, above
        }
        if (zones.isBuilding(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "Only blocks and the End Crystal can be part of a fortress.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHang(HangingPlaceEvent event) {
        if (event.getPlayer() != null && zones.isBuilding(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** In the build zone the crystal is a marker. It must not be hit, and never explode. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (inBuildWorld(event.getEntity().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (inBuildWorld(event.getEntity().getWorld().getName())) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    // --- the room -------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        BuildSession session = zones.sessionOf(event.getPlayer()).orElse(null);
        if (session == null) {
            return;
        }

        RoomButton button = BuildRoom.buttonAt(session.zone(), event.getClickedBlock());
        if (button == null) {
            return;
        }
        event.setCancelled(true);   // and the chest never opens as a chest

        switch (button) {
            case SAVE -> zones.save(event.getPlayer());
            case CLEAR -> zones.clear(event.getPlayer());
            case PALETTE -> new PaletteMenu(config.buildRules(), session.blueprint())
                    .open(event.getPlayer());
            case EXIT -> {
                if (session.dirty()) {
                    new ExitMenu(zones).open(event.getPlayer());
                } else {
                    zones.exit(event.getPlayer(), false);
                }
            }
        }
    }

    /**
     * The barrier shell is the wall. This is the net under it: a builder who ends up
     * outside their room — a cheat, a plugin teleport, a bug — is put back, rather than
     * left flying in creative over everybody else's fortress.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        BuildSession session = zones.sessionOf(event.getPlayer()).orElse(null);
        if (session == null || event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockY() == event.getFrom().getBlockY()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }

        if (!session.zone().isInRoom(event.getTo())) {
            event.getPlayer().teleport(session.zone().spawn());
        }
    }

    /** Nothing leaves a creative zone — not an item, not a block. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (zones.isBuilding(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A watcher leaves by sneaking twice — one sneak still flies them down. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            zones.handleWatcherSneak(event.getPlayer());
        }
    }

    /**
     * A watcher touches nothing.
     *
     * SPECTATOR already forbids all of this, and a watcher has no session, so every rule
     * above refuses them anyway. This is the belt on top of the braces: "somebody's mate
     * edited their fortress while watching" is not a bug I want to hear about from a player.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWatcherPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && zones.isWatching(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        zones.abandon(event.getPlayer());   // handles both builders and watchers
    }

    private boolean inBuildWorld(String worldName) {
        return zones.world() != null && zones.world().getName().equals(worldName);
    }

    private void refuse(BlockPlaceEvent event, Player player, String why) {
        event.setCancelled(true);
        player.sendActionBar(Component.text(why, NamedTextColor.RED));
    }

    /** The one number a builder actually wants: how many of these are left. */
    private void showBudget(Player player, BuildRules rules, Blueprint blueprint, String id) {
        player.sendActionBar(Component.text(id, NamedTextColor.GRAY)
                .append(Component.text("  " + rules.remaining(blueprint, id) + " left",
                        NamedTextColor.WHITE))
                .append(Component.text("   ·   " + blueprint.blockCount() + " blocks placed",
                        NamedTextColor.DARK_GRAY)));
    }
}

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
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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

        // A door is two blocks. Placed against the ceiling of the cube, its upper half lands
        // OUTSIDE — a block nothing owns: the cube scan never sees it, and the wipe between
        // builders never clears it. Refuse the whole placement rather than leave it behind.
        if (event instanceof BlockMultiPlaceEvent multi
                && multi.getReplacedBlockStates().stream()
                        .anyMatch(state -> !zone.isInCube(state.getBlock()))) {
            refuse(event, player, "It does not fit inside the cube.");
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

        record(session, zone, event);
        showBudget(player, rules, blueprint, id);
    }

    /**
     * Write what was placed into the live blueprint — <b>every block of it</b>.
     *
     * A door is two blocks and Minecraft fires <b>one</b> event for it. Recording only
     * {@code getBlock()} meant the budget counter thought a door cost one door, so a player
     * could place twice the doors the quota allows and only find out at save time, when the
     * re-scan of the world told the truth and turned their fortress into a draft.
     *
     * The state goes in too, not just the name: which way it faces, which half it is.
     */
    private static void record(BuildSession session, BuildZone zone, BlockPlaceEvent event) {
        Blueprint blueprint = session.blueprint();

        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState placed : multi.getReplacedBlockStates()) {
                Block block = placed.getBlock();
                if (zone.isInCube(block)) {
                    blueprint.set(zone.toBlueprint(block), block.getBlockData().getAsString());
                }
            }
        } else {
            Block block = event.getBlock();
            blueprint.set(zone.toBlueprint(block), block.getBlockData().getAsString());
        }
        session.touch();
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

    /**
     * A builder who leaves the build world by any route we did not drive is no longer a
     * builder.
     *
     * The hole this closes: a party leader can queue while a member is in a build zone. The
     * engine then teleports that member into a match — and their build session survived. Every
     * rule in this class keys off the session, so the build-zone rules would have started
     * applying to blocks they placed <b>in the arena</b>, their zone would have stayed locked
     * for good, and they would have arrived in a match in creative.
     *
     * Rather than enumerate the ways out (queued by someone else, an admin teleport, a plugin,
     * a world unload), treat the fact of leaving as the end of the session. It cannot be
     * bypassed, because it is not a check on the way out — it is the way out.
     */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (zones.world() == null || !zones.world().equals(event.getFrom())) {
            return;   // they were not in the build world to begin with
        }
        if (zones.isBuilding(player) || zones.isWatching(player)) {
            zones.abandon(player);
        }
    }

    /**
     * Nothing but a player may put a block in a fortress.
     *
     * A dispenser can place water, lava, or a boat; a piston can push a block into the
     * crystal's clearance. Neither fires a place event, so neither passes a single check, and
     * the save would simply record whatever they left behind. The validator would catch a
     * forbidden block eventually — but a rule you rely on being caught downstream is a rule
     * you have already stopped enforcing.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (inBuildWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (inBuildWorld(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (inBuildWorld(event.getBlock().getWorld().getName())) {
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

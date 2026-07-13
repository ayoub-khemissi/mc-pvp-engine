package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.domain.fortress.BlockIds;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.BuildReport;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.FortressValidator;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.PlacingItems;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import fr.ayoub.pvp.mode.fortress.storage.SavedFortress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The build zones: a void world holding N identical rooms, one per builder.
 *
 * A room is <b>built once</b> and then reused — the floor, the walls and the ceiling never
 * change, so only the cube inside it is wiped between two builders. Wiping a whole 50³ room
 * on every visit would be 125,000 block writes on the main thread; wiping the cube is 8,000,
 * and even that is worth not doing twice.
 */
public final class BuildZoneService {

    private static final int ROOM_Y = 64;

    /** Two sneaks within this delay mean "let me out", one means "go down". */
    private static final long DOUBLE_SNEAK_MILLIS = 500L;

    private static final Material FLOOR = Material.SMOOTH_STONE;
    private static final Material CUBE_FLOOR = Material.POLISHED_ANDESITE;   // shows where the cube is
    private static final Material WALL = Material.BARRIER;

    private final Plugin plugin;
    private final FortressConfig config;
    private final FortressRepository fortresses;

    private final Map<UUID, BuildSession> sessions = new HashMap<>();

    /** Who is watching whom build. A watcher is never a builder. */
    private final Map<UUID, UUID> watchers = new HashMap<>();
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    private final Set<Integer> occupied = new HashSet<>();
    private final Set<Integer> built = new HashSet<>();

    private World world;

    public BuildZoneService(Plugin plugin, FortressConfig config, FortressRepository fortresses) {
        this.plugin = plugin;
        this.config = config;
        this.fortresses = fortresses;
    }

    // --- the world ---------------------------------------------------------------

    /** Created on enable, so the first builder does not wait for a world. */
    public void createWorld() {
        world = Bukkit.getWorld(config.buildWorld());
        if (world != null) {
            return;
        }

        plugin.getLogger().info("Creating the fortress build world '" + config.buildWorld() + "'…");
        world = new WorldCreator(config.buildWorld())
                .generator(new VoidGenerator())
                .environment(World.Environment.NORMAL)
                .createWorld();

        if (world == null) {
            plugin.getLogger().severe("Could not create the build world. Nobody will be able to build.");
            return;
        }

        world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_TILE_DROPS, false);   // creative, but tidy
        world.setTime(6000);
    }

    public World world() {
        return world;
    }

    public Plugin plugin() {
        return plugin;
    }

    // --- sessions ----------------------------------------------------------------

    public Optional<BuildSession> sessionOf(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public boolean isBuilding(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Take a player into their build zone, on the slot they picked.
     *
     * The blueprint is loaded off the main thread; the world is only touched back on it.
     */
    public void enter(Player player, int slot) {
        if (world == null) {
            player.sendMessage(Component.text("The build world is missing — tell an admin.",
                    NamedTextColor.RED));
            return;
        }
        if (PvPEngineApi.lobby().isInMatch(player) || PvPEngineApi.lobby().isQueued(player)) {
            player.sendMessage(Component.text("Leave the queue first.", NamedTextColor.RED));
            return;
        }
        if (isBuilding(player)) {
            return;
        }

        Optional<BuildZone> free = allocate();
        if (free.isEmpty()) {
            player.sendMessage(Component.text(
                    "Every build zone is busy. Try again in a moment.", NamedTextColor.RED));
            return;
        }
        BuildZone zone = free.get();

        UUID id = player.getUniqueId();
        PvPEngineApi.storage().async().execute(() -> {
            Optional<SavedFortress> saved = fortresses.find(id, slot);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    release(zone);
                    return;
                }

                Blueprint blueprint = saved
                        .map(SavedFortress::blueprint)
                        .filter(existing -> existing.size() == config.fortressSize())
                        .orElseGet(() -> config.buildRules().emptyBlueprint());

                String name = saved.map(SavedFortress::name).orElse("Fortress " + slot);

                prepareRoom(zone);
                clearCube(zone);
                paste(zone, blueprint);
                if (blueprint.crystal() != null) {
                    spawnCrystal(zone, blueprint.crystal());
                }

                sessions.put(id, new BuildSession(id, zone, slot, name, blueprint));

                player.setGameMode(GameMode.CREATIVE);
                giveBuildingKit(player);
                player.teleport(zone.spawn());
                player.setAllowFlight(true);

                player.sendMessage(Component.text("Building ", NamedTextColor.GREEN)
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .append(Component.text(" (slot " + slot + ").", NamedTextColor.GREEN)));
                player.sendMessage(Component.text(
                        "Behind you: the board, and the SAVE / CLEAR / BLOCKS / EXIT buttons.",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "The golden arrow points at the FRONT of your fortress — "
                                + "that is where the enemy arrives.", NamedTextColor.GRAY));

                if (saved.isPresent() && saved.get().blueprint().size() != config.fortressSize()) {
                    player.sendMessage(Component.text("This fortress was built at "
                            + saved.get().blueprint().size() + "³ and the server now uses "
                            + config.fortressSize() + "³ — starting from an empty cube.",
                            NamedTextColor.YELLOW));
                }
            });
        });
    }

    /**
     * Save what is in the cube into the slot being edited.
     *
     * The cube is <b>read back from the world</b>, not taken from the blueprint we keep in
     * memory. The two are not the same thing, and the difference is not academic:
     * <ul>
     *   <li>A door is two blocks and Minecraft only fires one place event — the top half
     *       would never make it into memory, and the fortress would be pasted decapitated.</li>
     *   <li>A bucket does not fire a place event at all.</li>
     *   <li>Anything a future block or plugin does to the cube is caught for free.</li>
     * </ul>
     * The in-memory blueprint stays: it is what makes the live "37 obsidian left" counter
     * cost a map lookup instead of eight thousand block reads. It is a budget tracker, not
     * the truth. The world is the truth, and this is where we go and ask it.
     */
    public void save(Player player) {
        BuildSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Blueprint blueprint = scan(session.zone());
        session.replaceBlueprint(blueprint);   // and the counter re-syncs with reality

        BuildReport report = FortressValidator.validate(blueprint, config.buildRules());

        SavedFortress fortress = new SavedFortress(
                session.builder(), session.slot(), session.name(), blueprint.copy(),
                report.valid(), false);

        PvPEngineApi.storage().async().execute(() -> {
            fortresses.save(fortress);

            Bukkit.getScheduler().runTask(plugin, () -> {
                session.saved();
                if (!player.isOnline()) {
                    return;
                }

                if (report.valid()) {
                    player.sendMessage(Component.text("Saved. This fortress is ready to play.",
                            NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Saved as a draft — it cannot be played yet:",
                            NamedTextColor.YELLOW));
                    report.problems().forEach(problem ->
                            player.sendMessage(Component.text(" • " + problem, NamedTextColor.GRAY)));
                }
            });
        });
    }

    /** Wipe the cube. The slot on disk is untouched until they save. */
    public void clear(Player player) {
        sessionOf(player).ifPresent(session -> {
            session.blueprint().clear();
            clearCube(session.zone());
            session.touch();
            player.sendMessage(Component.text("Cleared. Nothing is saved until you press SAVE.",
                    NamedTextColor.YELLOW));
        });
    }

    /** Leave, keeping or dropping the changes. The slot is only overwritten by a save. */
    public void exit(Player player, boolean saveFirst) {
        BuildSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (saveFirst) {
            sessions.put(player.getUniqueId(), session);   // save() needs the session
            save(player);
            sessions.remove(player.getUniqueId());
        }

        dismissWatchers(player.getUniqueId());
        clearCube(session.zone());
        release(session.zone());

        player.getInventory().clear();   // nothing comes out of creative
        PvPEngineApi.lobby().sendToLobby(player);
    }

    // --- watching a teammate build ---------------------------------------------------

    public boolean isWatching(Player player) {
        return watchers.containsKey(player.getUniqueId());
    }

    /** Everyone in this player's party who is in a build zone right now. */
    public List<Player> buildingPartyMembers(Player player) {
        List<Player> building = new ArrayList<>();

        for (UUID member : PvPEngineApi.lobby().partyMembers(player)) {
            if (member.equals(player.getUniqueId())) {
                continue;
            }
            Player other = Bukkit.getPlayer(member);
            if (other != null && isBuilding(other)) {
                building.add(other);
            }
        }
        return building;
    }

    /**
     * Watch a party member build.
     *
     * A watcher is put in SPECTATOR: they fly, they pass through the walls, they cannot
     * touch a block, and — this is the part that matters — they have <b>no session</b>. Every
     * rule in {@link BuildListener} keys off the session, so a watcher is structurally
     * incapable of editing somebody else's fortress. It is not a permission check that can
     * be forgotten; there is simply nothing for them to edit with.
     */
    public void watch(Player viewer, Player builder) {
        BuildSession session = sessions.get(builder.getUniqueId());

        if (session == null || isBuilding(viewer) || isWatching(viewer)) {
            return;
        }
        if (!PvPEngineApi.lobby().partyMembers(viewer).contains(builder.getUniqueId())) {
            viewer.sendMessage(Component.text("You can only watch your own party.",
                    NamedTextColor.RED));
            return;
        }

        watchers.put(viewer.getUniqueId(), builder.getUniqueId());

        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleport(session.zone().center());

        viewer.sendMessage(Component.text("Watching ", NamedTextColor.AQUA)
                .append(Component.text(builder.getName(), NamedTextColor.WHITE))
                .append(Component.text(" build ", NamedTextColor.AQUA))
                .append(Component.text(session.name(), NamedTextColor.WHITE)));
        viewer.sendMessage(Component.text("Double-Shift to go back to the lobby.",
                NamedTextColor.GRAY));

        builder.sendMessage(Component.text(viewer.getName() + " is watching you build.",
                NamedTextColor.GRAY));
    }

    public void stopWatching(Player viewer) {
        if (watchers.remove(viewer.getUniqueId()) == null) {
            return;
        }
        lastSneak.remove(viewer.getUniqueId());

        if (viewer.isOnline()) {
            PvPEngineApi.lobby().sendToLobby(viewer);
        }
    }

    /**
     * Sneak is the only key a spectator can send the server — and it is also how they fly
     * down. So one sneak descends, two in quick succession leave. Same rule as the engine's
     * match spectator, because a player should not have to learn it twice.
     */
    public void handleWatcherSneak(Player viewer) {
        if (!isWatching(viewer)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastSneak.put(viewer.getUniqueId(), now);

        if (previous != null && now - previous <= DOUBLE_SNEAK_MILLIS) {
            stopWatching(viewer);
        }
    }

    /** The builder left. Nobody stays behind, staring at an empty room. */
    private void dismissWatchers(UUID builder) {
        for (UUID id : Set.copyOf(watchers.keySet())) {
            if (!builder.equals(watchers.get(id))) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                watchers.remove(id);
                continue;
            }
            viewer.sendMessage(Component.text("They stopped building.", NamedTextColor.YELLOW));
            stopWatching(viewer);
        }
    }

    /** A builder disconnected. Their zone must not stay locked forever. */
    public void abandon(Player player) {
        stopWatching(player);

        BuildSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        dismissWatchers(player.getUniqueId());
        clearCube(session.zone());
        release(session.zone());
    }

    public void abandonAll() {
        for (UUID id : Set.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                abandon(player);
            }
        }
    }

    // --- zones -------------------------------------------------------------------

    /**
     * The builder's kit: the whole palette, laid out.
     *
     * The hotbar holds the eight best blocks, hardest first, with the End Crystal on the far
     * right — where the eye goes for the thing that decides the match. The rest of the
     * palette fills the inventory below it, in the same order.
     *
     * Nobody should have to hunt through the creative inventory for a block the mode has
     * already decided they may use. The creative inventory is still there for the rest.
     */
    private void giveBuildingKit(Player player) {
        player.getInventory().clear();

        List<String> ordered = config.paletteByStrength();

        // Hotbar: the best eight.
        int hotbar = 0;
        int index = 0;
        for (; index < ordered.size() && hotbar < FortressConfig.HOTBAR_BLOCKS; index++) {
            ItemStack item = stackOf(ordered.get(index));
            if (item != null) {
                player.getInventory().setItem(hotbar++, item);
            }
        }

        player.getInventory().setItem(8, new ItemStack(Material.END_CRYSTAL, 1));

        // Everything else, filling the three rows of the inventory proper.
        int slot = 9;
        for (; index < ordered.size() && slot < 36; index++) {
            ItemStack item = stackOf(ordered.get(index));
            if (item != null) {
                player.getInventory().setItem(slot++, item);
            }
        }

        player.getInventory().setHeldItemSlot(0);
    }

    /** A block is not always an item — redstone dust on the ground has no item form. */
    private static ItemStack stackOf(String blockId) {
        Material item = PlacingItems.of(blockId);
        return item == null ? null : new ItemStack(item, 64);
    }

    private Optional<BuildZone> allocate() {
        for (int index = 0; index < config.zoneCount(); index++) {
            if (occupied.add(index)) {
                return Optional.of(zone(index));
            }
        }
        return Optional.empty();
    }

    private void release(BuildZone zone) {
        occupied.remove(zone.index());
    }

    private BuildZone zone(int index) {
        ZoneGeometry geometry = new ZoneGeometry(
                index,
                index * config.zoneSpacing(),
                ROOM_Y,
                0,
                config.zoneSize(),
                config.fortressSize());

        return new BuildZone(geometry, world);
    }

    /** Floor, walls, ceiling. Once per zone, per server run. */
    private void prepareRoom(BuildZone zone) {
        if (!built.add(zone.index())) {
            return;
        }

        ZoneGeometry g = zone.geometry();
        int x0 = g.roomX();
        int y0 = g.roomY();
        int z0 = g.roomZ();
        int room = g.roomSize();

        // Floor. The cube's footprint is a different colour, so the builder can see the
        // twenty by twenty they are allowed to fill without having to guess.
        for (int x = 0; x < room; x++) {
            for (int z = 0; z < room; z++) {
                boolean underCube = g.isInCube(x0 + x, g.cubeY(), z0 + z);
                world.getBlockAt(x0 + x, y0, z0 + z).setType(underCube ? CUBE_FLOOR : FLOOR);
            }
        }

        // The shell: barriers all around the room, one block outside it. Invisible, and
        // client-side — the builder simply cannot walk or fly through them.
        for (int x = -1; x <= room; x++) {
            for (int z = -1; z <= room; z++) {
                for (int y = -1; y <= room; y++) {
                    boolean shell = x == -1 || x == room || z == -1 || z == room
                            || y == -1 || y == room;
                    if (shell) {
                        world.getBlockAt(x0 + x, y0 + y, z0 + z).setType(WALL);
                    }
                }
            }
        }

        BuildRoom.furnish(zone);
    }

    private void clearCube(BuildZone zone) {
        int size = zone.cubeSize();

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    zone.toWorld(new BlockPos(x, y, z)).getBlock().setType(Material.AIR);
                }
            }
        }
        removeCrystals(zone);
    }

    /**
     * Read the cube back out of the world, block by block, plus wherever the crystal is.
     *
     * The <b>whole block state</b> is kept, not just the name: which way a stair faces, which
     * half of a door this is, whether a trapdoor is open, where a piston points. Store only
     * the name and a pasted fortress comes out with every stair facing east and every door in
     * two identical bottom halves.
     */
    private Blueprint scan(BuildZone zone) {
        Blueprint blueprint = config.buildRules().emptyBlueprint();
        int size = Math.min(zone.cubeSize(), blueprint.size());

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    Block block = zone.toWorld(new BlockPos(x, y, z)).getBlock();
                    if (!block.getType().isAir()) {
                        blueprint.set(x, y, z, block.getBlockData().getAsString());
                    }
                }
            }
        }

        for (Entity entity : zone.world().getEntitiesByClasses(EnderCrystal.class)) {
            Block block = entity.getLocation().getBlock();
            if (zone.isInCube(block)) {
                blueprint.crystal(zone.toBlueprint(block));
                break;
            }
        }
        return blueprint;
    }

    /**
     * Put a blueprint back into the world, states and all.
     *
     * A saved state may not survive a Minecraft update — a block can lose a property between
     * versions. Rather than let one unreadable cell abort the paste and leave half a fortress
     * standing, fall back to the plain block: a stair facing the wrong way is a nuisance, a
     * fortress with a hole in it is a lost match.
     */
    public static void paste(BuildZone zone, Blueprint blueprint) {
        int size = Math.min(blueprint.size(), zone.cubeSize());

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (blueprint.isAir(pos)) {
                        continue;
                    }
                    place(zone.toWorld(pos).getBlock(), blueprint.get(pos));
                }
            }
        }
    }

    private static void place(Block block, String state) {
        try {
            block.setBlockData(Bukkit.createBlockData(state), false);
            return;
        } catch (IllegalArgumentException e) {
            // The state did not survive a version change. Fall through to the plain block.
        }

        Material material = Material.matchMaterial(BlockIds.typeOf(state));
        if (material != null && material.isBlock()) {
            block.setType(material, false);
        }
    }

    public void spawnCrystal(BuildZone zone, BlockPos at) {
        removeCrystals(zone);

        Location location = zone.toWorld(at).add(0.5, 0, 0.5);
        EnderCrystal crystal = (EnderCrystal) zone.world().spawnEntity(location, EntityType.END_CRYSTAL);
        crystal.setShowingBottom(false);
        crystal.setInvulnerable(true);   // in the build zone it is a marker, not a target
    }

    public void removeCrystals(BuildZone zone) {
        for (Entity entity : zone.world().getEntitiesByClasses(EnderCrystal.class)) {
            Block block = entity.getLocation().getBlock();
            if (zone.isInCube(block)) {
                entity.remove();
            }
        }
    }
}

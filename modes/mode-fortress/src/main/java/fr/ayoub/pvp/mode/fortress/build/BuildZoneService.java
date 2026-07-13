package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.BuildReport;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.FortressValidator;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
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
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    private static final Material FLOOR = Material.SMOOTH_STONE;
    private static final Material CUBE_FLOOR = Material.POLISHED_ANDESITE;   // shows where the cube is
    private static final Material WALL = Material.BARRIER;

    private final Plugin plugin;
    private final FortressConfig config;
    private final FortressRepository fortresses;

    private final Map<UUID, BuildSession> sessions = new HashMap<>();
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

                sessions.put(id, new BuildSession(id, zone, slot, name, blueprint));

                player.setGameMode(GameMode.CREATIVE);
                giveHotbar(player);
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

        clearCube(session.zone());
        release(session.zone());

        player.getInventory().clear();   // nothing comes out of creative
        PvPEngineApi.lobby().sendToLobby(player);
    }

    /** A builder disconnected. Their zone must not stay locked forever. */
    public void abandon(Player player) {
        BuildSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
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
     * The hotbar: the eight best blocks, hardest first, and the End Crystal on the far
     * right — where a player's eye goes for the thing that matters most.
     *
     * The creative inventory is still there for the rest of the palette. This is only about
     * not making somebody search for obsidian every time they log in.
     */
    private void giveHotbar(Player player) {
        player.getInventory().clear();

        List<String> blocks = config.hotbarBlocks();
        for (int slot = 0; slot < blocks.size() && slot < FortressConfig.HOTBAR_BLOCKS; slot++) {
            Material material = Material.matchMaterial(blocks.get(slot));
            if (material != null) {
                player.getInventory().setItem(slot, new ItemStack(material, 64));
            }
        }

        player.getInventory().setItem(8, new ItemStack(Material.END_CRYSTAL, 1));
        player.getInventory().setHeldItemSlot(0);
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

    /** Read the cube back out of the world, block by block, plus wherever the crystal is. */
    private Blueprint scan(BuildZone zone) {
        Blueprint blueprint = config.buildRules().emptyBlueprint();
        int size = Math.min(zone.cubeSize(), blueprint.size());

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    Material material = zone.toWorld(new BlockPos(x, y, z)).getBlock().getType();
                    if (!material.isAir()) {
                        blueprint.set(x, y, z, material.name());
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

    private void paste(BuildZone zone, Blueprint blueprint) {
        int size = Math.min(blueprint.size(), zone.cubeSize());

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    String id = blueprint.get(x, y, z);
                    if (Blueprint.AIR.equals(id)) {
                        continue;
                    }
                    Material material = Material.matchMaterial(id);
                    if (material != null) {
                        zone.toWorld(new BlockPos(x, y, z)).getBlock().setType(material, false);
                    }
                }
            }
        }

        if (blueprint.crystal() != null) {
            spawnCrystal(zone, blueprint.crystal());
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

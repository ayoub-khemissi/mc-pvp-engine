package fr.ayoub.pvp.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Builds the test map in a void world: a lobby island, and N identical arena islands.
 *
 * <h2>Two walls, on purpose</h2>
 * <ul>
 *   <li><b>Barrier wall (client side).</b> {@code minecraft:barrier} is invisible but
 *       solid, and the <i>client</i> knows it — so the player is simply stopped, with no
 *       rubber-banding. This is the wall players actually feel.</li>
 *   <li><b>Region check (server side).</b> The safety net for what a client cannot be
 *       trusted with: cheats, ender pearls, teleports.</li>
 * </ul>
 *
 * The server bounds sit slightly <i>wider</i> than the barrier on purpose: if they were
 * the same, normal play would keep tripping the server check and we would be back to
 * rubber-banding.
 *
 * This map is a development scaffold. Real, designed maps drop in as a world plus a
 * map.yml — the engine only needs the team spawns and the bounds.
 */
public final class WorldSetup {

    // --- arenas ----------------------------------------------------------------

    /** Distance between two arenas. Well beyond any view distance. */
    public static final int ARENA_SPACING = 512;

    public static final int ARENA_Y = 64;
    public static final int ARENA_FLOOR_RADIUS = 20;

    /** How far either side of centre the team pads (and spawns) sit. */
    public static final int TEAM_PAD_OFFSET = 12;

    /** Height of the invisible barrier wall. Far beyond any jump or knockback. */
    public static final int WALL_HEIGHT = 10;

    /** Server-side limit: just outside the barrier, so it never fires in normal play. */
    public static final double ARENA_BOUNDS_RADIUS = 20.5;
    public static final int ARENA_CEILING = 100;

    // --- lobby -----------------------------------------------------------------

    public static final int LOBBY_Y = 100;
    public static final int LOBBY_RADIUS = 11;
    public static final int LOBBY_WALL_HEIGHT = 4;

    // --- palette ---------------------------------------------------------------

    private static final Material WALL = Material.BARRIER;              // invisible, solid
    private static final Material RAIL = Material.STONE_BRICK_WALL;     // the visible edge
    private static final Material PILLAR = Material.STONE_BRICKS;
    private static final Material PILLAR_TOP = Material.CHISELED_STONE_BRICKS;
    private static final Material LAMP = Material.SEA_LANTERN;

    private static final Material ARENA_FLOOR = Material.SMOOTH_STONE;
    private static final Material ARENA_RING = Material.POLISHED_ANDESITE;
    private static final Material ARENA_CENTER = Material.CHISELED_STONE_BRICKS;
    private static final Material TEAM_0_PAD = Material.RED_CONCRETE;
    private static final Material TEAM_1_PAD = Material.BLUE_CONCRETE;

    private static final Material LOBBY_FLOOR = Material.STONE_BRICKS;
    private static final Material LOBBY_ACCENT = Material.POLISHED_ANDESITE;
    private static final Material LOBBY_CENTER = Material.QUARTZ_BLOCK;

    private static final Material ROCK = Material.STONE;
    private static final Material DEEP_ROCK = Material.DEEPSLATE;

    private WorldSetup() {
    }

    public record Result(int arenas, int blocksPlaced) {
    }

    private static int placed;

    public static Result build(Plugin plugin, World world, int arenaCount) {
        clearGeneratedMaps(plugin);
        placed = 0;

        buildLobby(world);
        for (int index = 0; index < arenaCount; index++) {
            buildArena(world, index);
            writeMapFile(plugin, index);
        }
        return new Result(arenaCount, placed);
    }

    /**
     * Removes the maps we generate (and the shipped example), so running setup twice
     * cannot leave two arenas on the same spot. Designer maps are left alone.
     */
    private static void clearGeneratedMaps(Plugin plugin) {
        File folder = new File(plugin.getDataFolder(), "maps");
        File[] files = folder.listFiles((dir, name) ->
                name.equals("example.yml") || name.matches("arena-\\d+\\.yml"));

        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Lobby
    // ---------------------------------------------------------------------------

    private static void buildLobby(World world) {
        int y = LOBBY_Y;

        // Wipe first, so running setup again cannot leave old geometry behind —
        // in particular old invisible barriers, which are impossible to spot.
        clear(world,
                -LOBBY_RADIUS - 4, y - 8, -LOBBY_RADIUS - 4,
                LOBBY_RADIUS + 4, y + LOBBY_WALL_HEIGHT + 10, LOBBY_RADIUS + 4);

        // A floating island: the rock tapers away underneath.
        disc(world, 0, y - 1, 0, LOBBY_RADIUS - 1, ROCK);
        disc(world, 0, y - 2, 0, LOBBY_RADIUS - 3, ROCK);
        disc(world, 0, y - 3, 0, LOBBY_RADIUS - 6, DEEP_ROCK);
        disc(world, 0, y - 4, 0, LOBBY_RADIUS - 9, DEEP_ROCK);

        // Plaza: an andesite border, a stone-brick floor, a quartz circle in the middle.
        disc(world, 0, y, 0, LOBBY_RADIUS, LOBBY_ACCENT);
        disc(world, 0, y, 0, LOBBY_RADIUS - 2, LOBBY_FLOOR);
        disc(world, 0, y, 0, 3, LOBBY_CENTER);

        // A visible railing all around the rim, and an invisible wall above it.
        for (int dx = -LOBBY_RADIUS; dx <= LOBBY_RADIUS; dx++) {
            for (int dz = -LOBBY_RADIUS; dz <= LOBBY_RADIUS; dz++) {
                if (!inDisc(dx, dz, LOBBY_RADIUS) || !isRim(dx, dz, LOBBY_RADIUS)) {
                    continue;
                }
                set(world, dx, y + 1, dz, RAIL);
                for (int h = 2; h <= LOBBY_WALL_HEIGHT + 1; h++) {
                    set(world, dx, y + h, dz, WALL);
                }
            }
        }

        // Four lamp posts.
        int[][] posts = {{7, 7}, {-7, 7}, {7, -7}, {-7, -7}};
        for (int[] post : posts) {
            lampPost(world, post[0], y, post[1], 4);
        }

        // Two little huts and two trees, so it reads as a place rather than a slab.
        hut(world, -7, y + 1, 0);
        hut(world, 7, y + 1, 0);
        tree(world, 0, y + 1, -7);
        tree(world, 0, y + 1, 7);
    }

    /** A stone column topped with a sea lantern. */
    private static void lampPost(World world, int x, int y, int z, int height) {
        for (int h = 1; h <= height; h++) {
            set(world, x, y + h, z, PILLAR);
        }
        set(world, x, y + height + 1, z, PILLAR_TOP);
        set(world, x, y + height + 2, z, LAMP);
    }

    /** A 5x5 hut: log corners, plank walls, glass, an open doorway facing the middle. */
    private static void hut(World world, int centerX, int y, int centerZ) {
        int half = 2;
        int height = 3;

        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;

                if (edge) {
                    for (int h = 0; h < height; h++) {
                        Material material = corner
                                ? Material.OAK_LOG
                                : (h == 1 ? Material.GLASS_PANE : Material.OAK_PLANKS);
                        set(world, x, y + h, z, material);
                    }
                }
                // Roof.
                set(world, x, y + height, z, Material.OAK_SLAB);
            }
        }

        // Doorway, on the side facing the centre of the lobby.
        int doorX = centerX + (centerX > 0 ? -half : half);
        set(world, doorX, y, centerZ, Material.AIR);
        set(world, doorX, y + 1, centerZ, Material.AIR);
    }

    private static void tree(World world, int x, int y, int z) {
        for (int h = 0; h < 4; h++) {
            set(world, x, y + h, z, Material.OAK_LOG);
        }
        BlockData leaves = Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]");

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    int spread = (dy == 5) ? 1 : 2;
                    if (Math.abs(dx) > spread || Math.abs(dz) > spread) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy < 4) {
                        continue;   // keep the trunk
                    }
                    setData(world, x + dx, y + dy, z + dz, leaves);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Arenas
    // ---------------------------------------------------------------------------

    /** Arena i sits at x = (i + 1) * 512, z = 0. */
    public static int arenaCenterX(int index) {
        return (index + 1) * ARENA_SPACING;
    }

    private static void buildArena(World world, int index) {
        int cx = arenaCenterX(index);
        int r = ARENA_FLOOR_RADIUS;

        // Wipe first — see buildLobby. Only the grid arenas do this; a stamped one floats in the
        // air over decor it must not touch, so it clears nothing.
        clear(world,
                cx - r - 4, ARENA_Y - 8, -r - 4,
                cx + r + 4, ARENA_Y + WALL_HEIGHT + 10, r + 4);

        buildArena(world, cx, ARENA_Y, 0, true);
    }

    /** With the floating island — for the void grid, where a floor needs something under it. */
    public static void buildArena(World world, int cx, int floorY, int cz) {
        buildArena(world, cx, floorY, cz, true);
    }

    /**
     * The whole arena — floating island, ringed floor, railed rim with its invisible wall, eight lit
     * lamp posts, the two team pads — built at an anchor, with no clearing.
     *
     * <p>Parameterised so it is not tied to the fixed grid: the stamp wand drops this exact arena a
     * few blocks above a block you point at, so an imported map keeps its scenery and gains a clean,
     * detailed floor to fight on. The centre floor is at {@code floorY}; the team pads sit at
     * {@link #TEAM_PAD_OFFSET} either side of it, which is where the spawns go.
     */
    public static void buildArena(World world, int cx, int floorY, int cz, boolean withIsland) {
        int r = ARENA_FLOOR_RADIUS;

        // Floating island underneath — only where the arena hangs in the void. Stamped into a
        // carved pocket in someone's decor it just dangles as a detached blob, so it is skipped.
        if (withIsland) {
            disc(world, cx, floorY - 1, cz, r - 1, ROCK);
            disc(world, cx, floorY - 2, cz, r - 4, ROCK);
            disc(world, cx, floorY - 3, cz, r - 8, DEEP_ROCK);
            disc(world, cx, floorY - 4, cz, r - 13, DEEP_ROCK);
        }

        // Floor, with concentric rings so the space reads clearly.
        disc(world, cx, floorY, cz, r, ARENA_FLOOR);
        ring(world, cx, floorY, cz, 16, 18, ARENA_RING);
        ring(world, cx, floorY, cz, 10, 11, ARENA_RING);
        disc(world, cx, floorY, cz, 3, ARENA_CENTER);

        // The rim: stone brick floor, a visible railing, and the invisible wall above.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!inDisc(dx, dz, r) || !isRim(dx, dz, r)) {
                    continue;
                }
                set(world, cx + dx, floorY, cz + dz, PILLAR);
                set(world, cx + dx, floorY + 1, cz + dz, RAIL);
                for (int h = 2; h <= WALL_HEIGHT + 1; h++) {
                    set(world, cx + dx, floorY + h, cz + dz, WALL);
                }
            }
        }

        // Eight lit pillars around the edge — they frame the fight without blocking it.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int px = cx + (int) Math.round(Math.cos(angle) * 17);
            int pz = cz + (int) Math.round(Math.sin(angle) * 17);
            lampPost(world, px, floorY, pz, 3);
        }

        // Team pads, so you instantly know which side you are on.
        disc(world, cx - TEAM_PAD_OFFSET, floorY, cz, 2, TEAM_0_PAD);
        disc(world, cx + TEAM_PAD_OFFSET, floorY, cz, 2, TEAM_1_PAD);
    }

    // ---------------------------------------------------------------------------
    // Map file
    // ---------------------------------------------------------------------------

    private static void writeMapFile(Plugin plugin, int index) {
        int centerX = arenaCenterX(index);
        String id = "arena-" + (index + 1);

        YamlConfiguration map = new YamlConfiguration();
        map.set("id", id);
        map.set("world", plugin.getConfig().getString("world.name", "pvp"));

        map.set("spawns.team-0.x", centerX - 12 + 0.5);
        map.set("spawns.team-0.y", (double) ARENA_Y + 1);
        map.set("spawns.team-0.z", 0.5);
        map.set("spawns.team-0.yaw", -90.0);   // looking towards +X
        map.set("spawns.team-0.pitch", 0.0);

        map.set("spawns.team-1.x", centerX + 12 + 0.5);
        map.set("spawns.team-1.y", (double) ARENA_Y + 1);
        map.set("spawns.team-1.z", 0.5);
        map.set("spawns.team-1.yaw", 90.0);    // looking towards -X
        map.set("spawns.team-1.pitch", 0.0);

        map.set("bounds.type", "cylinder");
        map.set("bounds.center-x", (double) centerX);
        map.set("bounds.center-z", 0.0);
        map.set("bounds.radius", ARENA_BOUNDS_RADIUS);
        map.set("bounds.min-y", (double) ARENA_Y);
        map.set("bounds.max-y", (double) ARENA_CEILING);

        File folder = new File(plugin.getDataFolder(), "maps");
        folder.mkdirs();
        try {
            map.save(new File(folder, id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write map file " + id + ".yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // Drawing helpers
    // ---------------------------------------------------------------------------

    private static boolean inDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * On the rim if any of the four neighbours is off the disc.
     * Four (not eight) neighbours guarantees a ring with no diagonal gaps to slip through.
     */
    private static boolean isRim(int dx, int dz, int radius) {
        return !inDisc(dx + 1, dz, radius) || !inDisc(dx - 1, dz, radius)
                || !inDisc(dx, dz + 1, radius) || !inDisc(dx, dz - 1, radius);
    }

    /**
     * Empties a volume before building in it.
     *
     * Without this, {@code /pvpadmin setup} is not idempotent: blocks from a previous
     * layout survive inside the new one. That is merely ugly for stone — but for
     * {@code barrier} blocks it is invisible, so you end up walking into walls that
     * are not there any more. Clearing first is what makes a rebuild trustworthy.
     *
     * Not counted in the block total: this removes, it does not build.
     */
    private static void clear(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static void disc(World world, int cx, int y, int cz, int radius, Material material) {
        if (radius <= 0) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (inDisc(dx, dz, radius)) {
                    set(world, cx + dx, y, cz + dz, material);
                }
            }
        }
    }

    private static void ring(World world, int cx, int y, int cz, int inner, int outer, Material material) {
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                if (inDisc(dx, dz, outer) && !inDisc(dx, dz, inner)) {
                    set(world, cx + dx, y, cz + dz, material);
                }
            }
        }
    }

    private static void set(World world, int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != material) {
            block.setType(material, false);   // no physics: much faster
            placed++;
        }
    }

    private static void setData(World world, int x, int y, int z, BlockData data) {
        Block block = world.getBlockAt(x, y, z);
        if (!block.getBlockData().matches(data)) {
            block.setBlockData(data, false);
            placed++;
        }
    }
}

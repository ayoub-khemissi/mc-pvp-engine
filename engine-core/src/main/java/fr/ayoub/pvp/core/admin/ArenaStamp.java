package fr.ayoub.pvp.core.admin;

import fr.ayoub.pvp.core.world.WorldSetup;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stamps the full development arena into the air above a chosen block.
 *
 * <p>On an imported map the scenery is the good part; its floor is not. So rather than fight on it,
 * this drops the engine's own arena — the ringed floor and railed rim with its invisible wall and
 * eight lit lamp posts that {@code /pvpadmin setup} builds (minus the floating island, which just
 * dangles under a floating platform) — a few blocks
 * <b>above</b> the block you point at. Nothing below is touched: the arena floats over the decor,
 * which frames the fight without being part of it.
 *
 * <p>The geometry is {@link WorldSetup#buildArena}, so a stamped arena is byte-for-byte the dev
 * arena, not a lesser copy of it. This only adds the {@code map.yml} — the spawns and bounds that
 * make it queueable — and the reset volume, so a dug floor or a dropped item is gone by next match.
 */
public final class ArenaStamp {

    private ArenaStamp() {
    }

    /**
     * Build the arena at an anchor and write its {@code map.yml}.
     *
     * @param floorY the Y of the platform floor — the caller lifts it above the pointed block
     */
    public static void stamp(World world, int cx, int floorY, int cz,
                             String id, List<String> modes, File mapsFolder) throws IOException {
        // Re-stamping the same id must not leave the old arena's blocks — above all its invisible
        // barrier walls — floating where the new one no longer covers them. Clear the old volume,
        // wherever it was, before anything else.
        clearFromMapFile(new File(mapsFolder, id + ".yml"));

        int r = WorldSetup.ARENA_FLOOR_RADIUS;
        int pad = WorldSetup.TEAM_PAD_OFFSET;
        int height = WorldSetup.ARENA_CEILING - WorldSetup.ARENA_Y;

        // The box the arena lives in — a generous margin around it and headroom above the wall.
        int minX = cx - r - 2;
        int maxX = cx + r + 2;
        int minZ = cz - r - 2;
        int maxZ = cz + r + 2;
        int minY = floorY - 5;                                   // below the floating island
        int maxY = floorY + WorldSetup.WALL_HEIGHT + 10;         // above the wall, room to fight

        // Carve it out first: clear everything inside, so any decor that pokes into the arena — a
        // roof, a pillar, a wall of the building it sits in — is gone before the floor goes down.
        clear(world, minX, minY, minZ, maxX, maxY, maxZ);

        WorldSetup.buildArena(world, cx, floorY, cz, false);   // no floating island — see WorldSetup

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("world", world.getName());
        yaml.set("modes", modes.isEmpty() ? List.of("duel") : modes);

        // Three spawns a team, spread across the pad, so a 3v3 does not stack on one block. Team 0
        // looks +X (yaw -90), team 1 looks -X (yaw 90): straight at each other.
        yaml.set("spawns.team-0", spread(cx - pad, floorY + 1, cz, -90f));
        yaml.set("spawns.team-1", spread(cx + pad, floorY + 1, cz, 90f));

        yaml.set("bounds.type", "cylinder");
        yaml.set("bounds.center-x", cx + 0.5);
        yaml.set("bounds.center-z", cz + 0.5);
        yaml.set("bounds.radius", WorldSetup.ARENA_BOUNDS_RADIUS);
        yaml.set("bounds.min-y", (double) floorY);
        yaml.set("bounds.max-y", (double) floorY + height);

        // The reset box IS the carved box, so a match restores the arena and the empty pocket it
        // sits in exactly as they were stamped — and never touches the decor outside it.
        Map<String, Object> box = new LinkedHashMap<>();
        box.put("min-x", minX);
        box.put("min-y", minY);
        box.put("min-z", minZ);
        box.put("max-x", maxX);
        box.put("max-y", maxY);
        box.put("max-z", maxZ);
        yaml.set("reset", List.of(box));

        mapsFolder.mkdirs();
        yaml.save(new File(mapsFolder, id + ".yml"));
    }

    /**
     * Clear the blocks of the arena a {@code map.yml} describes — its whole reset volume, barrier
     * walls and all. Used to wipe a stamped arena before it is re-stamped or removed. Does nothing
     * if the file, or the world it names, is not there.
     */
    public static void clearFromMapFile(File yaml) {
        if (!yaml.exists()) {
            return;
        }
        YamlConfiguration map = YamlConfiguration.loadConfiguration(yaml);
        World world = org.bukkit.Bukkit.getWorld(map.getString("world", ""));
        if (world == null) {
            return;
        }
        for (Map<?, ?> box : map.getMapList("reset")) {
            clear(world, num(box, "min-x"), num(box, "min-y"), num(box, "min-z"),
                    num(box, "max-x"), num(box, "max-y"), num(box, "max-z"));
        }
    }

    private static int num(Map<?, ?> box, String key) {
        return box.get(key) instanceof Number n ? n.intValue() : 0;
    }

    /** Empty a box to air, so the arena is built into a clean pocket rather than through decor. */
    private static void clear(World world, int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setType(org.bukkit.Material.AIR, false);
                    }
                }
            }
        }
    }

    /** Three spawn points in a line, centred on the pad. */
    private static List<Map<String, Object>> spread(int x, int y, int cz, float yaw) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int dz = -2; dz <= 2; dz += 2) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("x", x + 0.5);
            p.put("y", (double) y);
            p.put("z", cz + dz + 0.5);
            p.put("yaw", (double) yaw);
            p.put("pitch", 0.0);
            points.add(p);
        }
        return points;
    }
}

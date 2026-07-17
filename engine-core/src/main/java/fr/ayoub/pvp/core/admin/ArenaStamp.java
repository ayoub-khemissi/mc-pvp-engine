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
 * this drops the engine's own arena — the same one {@code /pvpadmin setup} builds, floating island
 * and ringed floor and railed rim with its invisible wall and eight lit lamp posts — a few blocks
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
     * @param floorY the Y of the platform floor — the caller lifts it above the pointed block so the
     *               floating island underneath still clears the decor
     */
    public static void stamp(World world, int cx, int floorY, int cz,
                             String id, List<String> modes, File mapsFolder) throws IOException {
        WorldSetup.buildArena(world, cx, floorY, cz);

        int r = WorldSetup.ARENA_FLOOR_RADIUS;
        int pad = WorldSetup.TEAM_PAD_OFFSET;
        int height = WorldSetup.ARENA_CEILING - WorldSetup.ARENA_Y;

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

        // The reset box hugs the arena — down to just below the island, up past the wall — so it
        // never reaches the decor below, whatever the lift.
        Map<String, Object> box = new LinkedHashMap<>();
        box.put("min-x", cx - r - 2);
        box.put("min-y", floorY - 5);
        box.put("min-z", cz - r - 2);
        box.put("max-x", cx + r + 2);
        box.put("max-y", floorY + WorldSetup.WALL_HEIGHT + 2);
        box.put("max-z", cz + r + 2);
        yaml.set("reset", List.of(box));

        mapsFolder.mkdirs();
        yaml.save(new File(mapsFolder, id + ".yml"));
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

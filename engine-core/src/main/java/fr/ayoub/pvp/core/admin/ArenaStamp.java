package fr.ayoub.pvp.core.admin;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stamps a clean fighting arena into the air above a chosen block.
 *
 * <p>On an imported map the scenery is the good part; its floor is not. So rather than fight on it,
 * this drops a small, controlled platform — a floor to stand on, an invisible wall round the edge,
 * two facing spawns — a few blocks <b>above</b> the block you point at. Nothing below is touched: the
 * arena floats over the decor, which frames the fight without being part of it.
 *
 * <p>The platform is a real floor of blocks (it hangs in the air, so it needs one), the wall is the
 * same two layers as every arena — a {@code barrier} the client will not let you cross, and a
 * slightly wider server bound behind it. The whole box is the reset volume, so a dug-out floor or a
 * dropped item is gone by the next match.
 */
public final class ArenaStamp {

    private static final Material FLOOR = Material.SMOOTH_STONE;
    private static final Material RING = Material.POLISHED_ANDESITE;
    private static final Material CENTER = Material.CHISELED_STONE_BRICKS;
    private static final Material RAIL = Material.SMOOTH_STONE_SLAB;
    private static final Material TEAM_0 = Material.RED_TERRACOTTA;
    private static final Material TEAM_1 = Material.BLUE_TERRACOTTA;

    private static final int WALL_HEIGHT = 8;
    private static final int CEILING = 40;

    private ArenaStamp() {
    }

    /**
     * Build the arena and write its {@code map.yml}.
     *
     * @param floorY the Y of the platform floor — the caller lifts it above the pointed block so the
     *               decor underneath is left alone
     * @param radius the floor radius; ~12 fits inside most decorated arenas
     */
    public static void stamp(World world, int cx, int floorY, int cz, int radius,
                             String id, List<String> modes, File mapsFolder) throws IOException {
        // Floor, with a couple of rings so the space reads clearly, on empty air.
        disc(world, cx, floorY, cz, radius, FLOOR);
        ringBand(world, cx, floorY, cz, radius - 4, radius - 3, RING);
        disc(world, cx, floorY, cz, 2, CENTER);

        // The rim: a low visible rail, then the invisible barrier wall above it.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!inDisc(dx, dz, radius) || inDisc(dx, dz, radius - 1)) {
                    continue;
                }
                world.getBlockAt(cx + dx, floorY + 1, cz + dz).setType(RAIL, false);
                for (int h = 2; h <= WALL_HEIGHT + 1; h++) {
                    world.getBlockAt(cx + dx, floorY + h, cz + dz).setType(Material.BARRIER, false);
                }
            }
        }

        // Team pads, so each side knows where it stands.
        int pad = radius - 5;
        disc(world, cx - pad, floorY, cz, 2, TEAM_0);
        disc(world, cx + pad, floorY, cz, 2, TEAM_1);

        write(world, cx, floorY, cz, radius, pad, id, modes, mapsFolder);
    }

    private static void write(World world, int cx, int floorY, int cz, int radius, int pad,
                              String id, List<String> modes, File mapsFolder) throws IOException {
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
        yaml.set("bounds.radius", radius + 0.5);
        yaml.set("bounds.min-y", (double) floorY);
        yaml.set("bounds.max-y", (double) floorY + CEILING);

        Map<String, Object> box = new LinkedHashMap<>();
        box.put("min-x", cx - radius - 1);
        box.put("min-y", floorY - 1);
        box.put("min-z", cz - radius - 1);
        box.put("max-x", cx + radius + 1);
        box.put("max-y", floorY + WALL_HEIGHT + 2);
        box.put("max-z", cz + radius + 1);
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

    // --- geometry -------------------------------------------------------------------------

    private static void disc(World world, int cx, int y, int cz, int radius, Material material) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (inDisc(dx, dz, radius)) {
                    world.getBlockAt(cx + dx, y, cz + dz).setType(material, false);
                }
            }
        }
    }

    private static void ringBand(World world, int cx, int y, int cz, int inner, int outer, Material m) {
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                if (inDisc(dx, dz, outer) && !inDisc(dx, dz, inner)) {
                    world.getBlockAt(cx + dx, y, cz + dz).setType(m, false);
                }
            }
        }
    }

    private static boolean inDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }
}

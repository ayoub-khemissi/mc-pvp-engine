package fr.ayoub.pvp.core.admin;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map being defined in-game, one marker at a time, before it is written to a {@code map.yml}.
 *
 * <p>A third-party arena — a Chunkity duel arena, a designer's build — arrives as blocks in a world
 * with none of the engine's markers on it: no team spawns, no bounds. Rather than guess coordinates,
 * an admin flies into the arena and stamps them: stand where a fighter should appear and add a
 * spawn, stand at two opposite corners and mark the bounds. This holds those stamps until they are
 * saved, and then writes exactly the {@code map.yml} the engine already reads.
 *
 * <p>Each team gets <b>several</b> spawns, added one at a time — enough for the biggest format the
 * arena will host, so a 3v3 does not stack three fighters on one block. One spawn per team is the
 * minimum; add more for 2v2 and 3v3.
 *
 * <p>The two corners double as the <b>reset volume</b>: the box is both the invisible wall and the
 * region the engine photographs and restores between matches. One box, two jobs — which is why a
 * decaying leaf or a dug-out floor never survives into the next duel.
 */
public final class MapDraft {

    private final String id;
    private final String world;
    private final List<String> modes = new ArrayList<>();

    private final List<Location> team0 = new ArrayList<>();
    private final List<Location> team1 = new ArrayList<>();
    private Location corner1;
    private Location corner2;

    public MapDraft(String id, String world) {
        this.id = id;
        this.world = world;
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    /** Add a spawn to a team. Returns how many that team now has. */
    public int addSpawn(int team, Location at) {
        List<Location> spawns = team == 0 ? team0 : team1;
        spawns.add(at.clone());
        return spawns.size();
    }

    public void clearSpawns(int team) {
        (team == 0 ? team0 : team1).clear();
    }

    public void setCorner(int which, Location at) {
        if (which == 1) {
            corner1 = at.clone();
        } else {
            corner2 = at.clone();
        }
    }

    public void setModes(List<String> ids) {
        modes.clear();
        modes.addAll(ids);
    }

    /** What is still missing, in words, so the admin knows what to stamp next. Empty when ready. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (team0.isEmpty()) {
            missing.add("at least one spawn for team 0");
        }
        if (team1.isEmpty()) {
            missing.add("at least one spawn for team 1");
        }
        if (corner1 == null) {
            missing.add("corner 1");
        }
        if (corner2 == null) {
            missing.add("corner 2");
        }
        return missing;
    }

    public boolean isReady() {
        return missing().isEmpty();
    }

    /** How many spawns each team has, e.g. "team 0: 3, team 1: 3" — supports up to 3v3 here. */
    public String spawnCounts() {
        return "team 0: " + team0.size() + ", team 1: " + team1.size();
    }

    /**
     * Write the {@code map.yml}. The bounds are a cuboid spanning the two corners, and the same box
     * is written as the reset volume, so the arena is put back between matches.
     */
    public void save(File mapsFolder) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("world", world);
        yaml.set("modes", modes.isEmpty() ? List.of("duel") : modes);

        yaml.set("spawns.team-0", team0.stream().map(MapDraft::point).toList());
        yaml.set("spawns.team-1", team1.stream().map(MapDraft::point).toList());

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        yaml.set("bounds.type", "cuboid");
        yaml.set("bounds.min-x", (double) minX);
        yaml.set("bounds.min-y", (double) minY);
        yaml.set("bounds.min-z", (double) minZ);
        yaml.set("bounds.max-x", (double) maxX + 1);
        yaml.set("bounds.max-y", (double) maxY + 1);
        yaml.set("bounds.max-z", (double) maxZ + 1);

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

    private static Map<String, Object> point(Location at) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", at.getX());
        map.put("y", at.getY());
        map.put("z", at.getZ());
        map.put("yaw", (double) at.getYaw());
        map.put("pitch", (double) at.getPitch());
        return map;
    }
}

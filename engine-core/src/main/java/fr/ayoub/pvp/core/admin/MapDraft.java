package fr.ayoub.pvp.core.admin;

import fr.ayoub.pvp.domain.arena.CardinalFacing;
import fr.ayoub.pvp.domain.arena.SpawnCoherence;
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
 * <p>A third-party arena arrives as blocks in a world with none of the engine's markers on it — no
 * team spawns, no bounds. The admin flies in and stamps them; this holds the stamps until they are
 * saved, then writes exactly the {@code map.yml} the engine reads.
 *
 * <p><b>Spawns are cleaned as they are added:</b> centred on the block (x.5 / z.5, so a fighter is
 * not wedged in a corner) and their look snapped to a straight direction (0/90/180/270, so two
 * fighters actually face each other). Each team gets several — enough for the biggest format the
 * arena hosts, so a 3v3 does not stack three people on one block.
 *
 * <p>The two corners double as the <b>reset volume</b>: the box is both the invisible wall and the
 * region the engine photographs and restores between matches.
 */
public final class MapDraft {

    /** How close you must stand to an existing spawn for a second click to remove it. */
    private static final double TOGGLE_RADIUS = 1.5;

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

    private List<Location> team(int team) {
        return team == 0 ? team0 : team1;
    }

    /**
     * Add a spawn, centred on the block and facing a straight direction. Returns how many that team
     * now has. Standing within {@link #TOGGLE_RADIUS} of one you already placed removes it instead —
     * so a second click where you stand is an "undo", which is what {@link #toggleSpawn} does.
     */
    public int addSpawn(int team, Location at) {
        Location clean = at.clone();
        clean.setX(clean.getBlockX() + 0.5);
        clean.setZ(clean.getBlockZ() + 0.5);
        clean.setY(clean.getBlockY());
        clean.setYaw(CardinalFacing.snap(clean.getYaw()));
        clean.setPitch(0);
        team(team).add(clean);
        return team(team).size();
    }

    /**
     * Click near a spawn you placed → remove it; click on open ground → add one.
     *
     * @return {@code +n} when a spawn was added (the new count), {@code -n} when one was removed
     */
    public int toggleSpawn(int teamIndex, Location at) {
        List<Location> spawns = team(teamIndex);
        for (int i = 0; i < spawns.size(); i++) {
            if (near(spawns.get(i), at)) {
                spawns.remove(i);
                return -spawns.size();
            }
        }
        return addSpawn(teamIndex, at);
    }

    private static boolean near(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.distanceSquared(new Location(b.getWorld(),
                        b.getBlockX() + 0.5, b.getBlockY(), b.getBlockZ() + 0.5))
                    <= TOGGLE_RADIUS * TOGGLE_RADIUS;
    }

    public void clearSpawns(int team) {
        team(team).clear();
    }

    public List<Location> spawns(int team) {
        return List.copyOf(team(team));
    }

    /** Set a corner, or clear it if it is already at the block you are standing on. */
    public void toggleCorner(int which, Location at) {
        Location existing = which == 1 ? corner1 : corner2;
        boolean sameBlock = existing != null
                && existing.getBlockX() == at.getBlockX()
                && existing.getBlockY() == at.getBlockY()
                && existing.getBlockZ() == at.getBlockZ();

        Location value = sameBlock ? null : at.clone();
        if (which == 1) {
            corner1 = value;
        } else {
            corner2 = value;
        }
    }

    public Location corner(int which) {
        return which == 1 ? corner1 : corner2;
    }

    public void clearAll() {
        team0.clear();
        team1.clear();
        corner1 = null;
        corner2 = null;
    }

    public void setModes(List<String> ids) {
        modes.clear();
        modes.addAll(ids);
    }

    /** What is still missing, in words. Empty when the map has everything it needs. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (team0.isEmpty()) {
            missing.add("a spawn for team 0");
        }
        if (team1.isEmpty()) {
            missing.add("a spawn for team 1");
        }
        if (corner1 == null) {
            missing.add("corner 1");
        }
        if (corner2 == null) {
            missing.add("corner 2");
        }
        return missing;
    }

    /**
     * Problems that do not block saving but the admin should see — chiefly whether the two teams
     * actually face each other (see {@link SpawnCoherence}), and whether the bounds are a real box
     * rather than a flat slab, which is the mistake that ships a broken arena.
     */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>(SpawnCoherence.check(yaws(team0), yaws(team1)));

        if (corner1 != null && corner2 != null) {
            int h = Math.abs(corner1.getBlockY() - corner2.getBlockY()) + 1;
            int w = Math.abs(corner1.getBlockX() - corner2.getBlockX()) + 1;
            int d = Math.abs(corner1.getBlockZ() - corner2.getBlockZ()) + 1;
            if (h < 5 || w < 5 || d < 5) {
                warnings.add("the bounds are very thin (" + w + "x" + h + "x" + d
                        + ") — corner 2 should be high above the arena, not next to corner 1");
            }
        }
        return warnings;
    }

    private static List<Float> yaws(List<Location> spawns) {
        return spawns.stream().map(Location::getYaw).toList();
    }

    public boolean isReady() {
        return missing().isEmpty();
    }

    public String status() {
        return "team 0: " + team0.size() + " spawn(s), team 1: " + team1.size()
                + " spawn(s), corner 1 " + (corner1 != null ? "set" : "—")
                + ", corner 2 " + (corner2 != null ? "set" : "—");
    }

    /**
     * Write the {@code map.yml}. Bounds are a cuboid spanning the two corners, and the same box is
     * written as the reset volume so the arena is put back between matches.
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

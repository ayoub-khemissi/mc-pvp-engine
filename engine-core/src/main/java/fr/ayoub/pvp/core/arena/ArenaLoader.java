package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.arena.MapDescriptor;
import fr.ayoub.pvp.domain.region.Region;
import fr.ayoub.pvp.domain.region.RegionParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code plugins/PvPEngine/maps/*.yml}.
 *
 * A bad map file is reported and skipped — it never takes the server down.
 */
public final class ArenaLoader {

    private ArenaLoader() {
    }

    public static List<Arena> loadAll(Plugin plugin) {
        File folder = new File(plugin.getDataFolder(), "maps");
        if (!folder.exists()) {
            folder.mkdirs();
            // A reference, NOT a map: the ".template" suffix keeps it out of the listing
            // below. If it were a real .yml it would count as an existing map, which would
            // stop the engine from generating its development map on a fresh server — and
            // it would sit on top of arena-1.
            plugin.saveResource("maps/example.yml.template", false);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return List.of();
        }

        List<Arena> arenas = new ArrayList<>();
        for (File file : files) {
            try {
                arenas.add(load(file));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping map '" + file.getName() + "': " + e.getMessage());
            }
        }
        return arenas;
    }

    private static Arena load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String id = yaml.getString("id", file.getName().replace(".yml", ""));

        String worldName = yaml.getString("world");
        if (worldName == null) {
            throw new IllegalArgumentException("missing 'world'");
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("world '" + worldName + "' is not loaded");
        }

        Region bounds = RegionParser.parse(boundsOf(yaml));
        List<Location> spawns = spawnsOf(yaml, world);

        if (spawns.size() < 2) {
            throw new IllegalArgumentException("a map needs at least 2 team spawns, found " + spawns.size());
        }

        // Optional. No 'modes' = any mode. No 'rating' = any rating.
        Set<String> modes = new HashSet<>(yaml.getStringList("modes"));
        int minRating = yaml.getInt("rating.min", 0);
        int maxRating = yaml.getInt("rating.max", Integer.MAX_VALUE);

        MapDescriptor descriptor = new MapDescriptor(id, modes, minRating, maxRating);

        // Optional, and the map is the only thing that knows: see Arena.Render.
        Arena.Render render = new Arena.Render(
                yaml.getInt("view-distance", 0),
                yaml.getInt("simulation-distance", 0));

        return new Arena(descriptor, world, spawns, bounds, markersOf(yaml, world), render,
                resetOf(yaml));
    }

    /**
     * The 'reset' block: the boxes of the map that a match may wreck, and the engine puts back.
     *
     * <pre>
     * reset:
     *   - {min-x: 0, min-y: 40, min-z: 0, max-x: 127, max-y: 96, max-z: 127}
     * </pre>
     *
     * <p>Deliberately NOT the bounds. The bounds are the invisible wall — where a player may go —
     * and in Fortress that reaches a thousand blocks down Z to take in the voting plains, because a
     * player standing on one must not be dragged back. Photographing that would mean photographing
     * eight million blocks of empty sky. What must be restored is only what can be CHANGED.
     *
     * <p>A map that lists nothing is never reset. That is the right default for a map nobody can
     * damage, and the wrong one for a map they can — so it is the map that says.
     */
    private static List<Volume> resetOf(YamlConfiguration yaml) {
        List<Volume> volumes = new ArrayList<>();

        for (Map<?, ?> box : yaml.getMapList("reset")) {
            volumes.add(new Volume(
                    number(box, "min-x"), number(box, "min-y"), number(box, "min-z"),
                    number(box, "max-x"), number(box, "max-y"), number(box, "max-z")));
        }
        return volumes;
    }

    private static int number(Map<?, ?> box, String key) {
        Object value = box.get(key);
        if (!(value instanceof Number found)) {
            throw new IllegalArgumentException("a 'reset' box is missing '" + key + "'");
        }
        return found.intValue();
    }

    /**
     * The 'markers' block: named points the engine carries and never interprets.
     *
     * <pre>
     * markers:
     *   fortress-pad-0: {x: .., y: .., z: ..}
     * </pre>
     *
     * A mode asks for the name it knows. The engine learns nothing — which is what stops a
     * new mode from needing a new engine, and is the vocabulary a designer's map will be
     * imported with.
     */
    private static Map<String, Location> markersOf(YamlConfiguration yaml, World world) {
        ConfigurationSection section = yaml.getConfigurationSection("markers");
        if (section == null) {
            return Map.of();
        }

        Map<String, Location> markers = new HashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection at = section.getConfigurationSection(name);
            if (at == null) {
                continue;
            }
            markers.put(name, new Location(
                    world,
                    at.getDouble("x"),
                    at.getDouble("y"),
                    at.getDouble("z"),
                    (float) at.getDouble("yaw"),
                    (float) at.getDouble("pitch")));
        }
        return markers;
    }

    /** The 'bounds' block, flattened to a plain map so the pure parser can read it. */
    private static Map<String, Object> boundsOf(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("bounds");
        if (section == null) {
            throw new IllegalArgumentException("missing 'bounds'");
        }
        return section.getValues(false);
    }

    private static List<Location> spawnsOf(YamlConfiguration yaml, World world) {
        ConfigurationSection section = yaml.getConfigurationSection("spawns");
        if (section == null) {
            throw new IllegalArgumentException("missing 'spawns'");
        }

        List<Location> spawns = new ArrayList<>();
        for (int team = 0; ; team++) {
            ConfigurationSection spawn = section.getConfigurationSection("team-" + team);
            if (spawn == null) {
                break;
            }
            spawns.add(new Location(
                    world,
                    spawn.getDouble("x"),
                    spawn.getDouble("y"),
                    spawn.getDouble("z"),
                    (float) spawn.getDouble("yaw"),
                    (float) spawn.getDouble("pitch")));
        }
        return spawns;
    }
}

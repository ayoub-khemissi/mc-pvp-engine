package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.domain.fortress.BuildRules;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * config.yml, turned into the numbers the mode actually runs on.
 *
 * Nothing about Fortress is settled — the cube size, the block list, the quotas, the room
 * around the builder. So nothing about it is a constant: it is all read here, once, and
 * passed down. If you find a literal 20 anywhere else in this mode, it is a bug.
 *
 * A block name that Minecraft does not know is <b>reported and skipped</b>, not fatal: a
 * typo in the palette must not take the server down.
 */
public final class FortressConfig {

    private final BuildRules buildRules;

    private final int slots;
    private final String buildWorld;
    private final int zoneSize;
    private final int zoneSpacing;
    private final int zoneCount;

    public FortressConfig(FileConfiguration config, Logger logger) {
        int size = config.getInt("fortress.size", 20);

        this.slots = Math.max(1, config.getInt("fortress.slots", 3));
        this.buildWorld = config.getString("build.world", "fortress_build");
        this.zoneSize = Math.max(size + 4, config.getInt("build.size", 50));
        this.zoneSpacing = Math.max(zoneSize + 16, config.getInt("build.spacing", 128));
        this.zoneCount = Math.max(1, config.getInt("build.instances", 8));

        this.buildRules = new BuildRules(
                size,
                readPalette(config, logger),
                config.getString("fortress.crystal.base", "OBSIDIAN"),
                config.getInt("fortress.crystal.clearance-radius", 1),
                config.getInt("fortress.crystal.clearance-height", 3));
    }

    private static Map<String, Integer> readPalette(FileConfiguration config, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection("palette");
        Map<String, Integer> palette = new LinkedHashMap<>();

        if (section == null) {
            logger.warning("No 'palette' in config.yml — nobody will be able to build anything.");
            return palette;
        }

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isBlock()) {
                logger.warning("Palette: '" + key + "' is not a block. Skipped.");
                continue;
            }
            int quota = section.getInt(key);
            if (quota > 0) {
                palette.put(material.name(), quota);
            }
        }
        return palette;
    }

    public BuildRules buildRules() {
        return buildRules;
    }

    /** The buildable cube, in blocks. */
    public int fortressSize() {
        return buildRules.size();
    }

    public int slots() {
        return slots;
    }

    public String buildWorld() {
        return buildWorld;
    }

    /** The room around the builder — walls, floor, ceiling. */
    public int zoneSize() {
        return zoneSize;
    }

    public int zoneSpacing() {
        return zoneSpacing;
    }

    /** How many people can be in a build zone at the same time. */
    public int zoneCount() {
        return zoneCount;
    }
}

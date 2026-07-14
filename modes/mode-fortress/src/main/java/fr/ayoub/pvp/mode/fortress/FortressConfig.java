package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.domain.fortress.CrystalRules;
import fr.ayoub.pvp.domain.fortress.BuildRules;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Eight blocks and the crystal — a hotbar is nine slots wide. */
    public static final int HOTBAR_BLOCKS = 8;

    private final BuildRules buildRules;
    private final List<String> ordered;

    private final int slots;
    private final int respawnSeconds;
    private final int spawnProtectionSeconds;
    private final int crystalHealth;
    private final long crystalHitCooldownTicks;
    private final CrystalRules crystalRules;
    private final int voteSeconds;
    private final String buildWorld;
    private final int buildViewDistance;
    private final int zoneSize;
    private final int zoneSpacing;
    private final int zoneCount;

    public FortressConfig(FileConfiguration config, Logger logger) {
        int size = config.getInt("fortress.size", 20);

        this.slots = Math.max(1, config.getInt("fortress.slots", 3));
        this.respawnSeconds = Math.max(0, config.getInt("fortress.respawn-seconds", 10));
        this.spawnProtectionSeconds =
                Math.max(0, config.getInt("fortress.spawn-protection-seconds", 5));
        this.crystalHealth = Math.max(1, config.getInt("fortress.crystal.health", 250));
        this.crystalHitCooldownTicks =
                Math.max(0, config.getLong("fortress.crystal.hit-cooldown-ticks", 10));
        this.crystalRules = new CrystalRules(
                Math.max(0, config.getDouble("fortress.crystal.damage.melee", 1.0)),
                Math.max(0, config.getDouble("fortress.crystal.damage.projectile", 1.0)),
                Math.max(0, config.getDouble("fortress.crystal.damage.explosion", 1.0)),
                Math.max(0, config.getDouble("fortress.crystal.damage.other", 1.0)));
        this.voteSeconds = Math.max(5, config.getInt("fortress.vote-seconds", 30));
        this.buildWorld = config.getString("build.world", "fortress_build");
        this.buildViewDistance = config.getInt("build.view-distance", 5);
        this.zoneSize = Math.max(size + 4, config.getInt("build.size", 50));
        this.zoneSpacing = Math.max(zoneSize + 16, config.getInt("build.spacing", 128));
        this.zoneCount = Math.max(1, config.getInt("build.instances", 8));

        this.buildRules = new BuildRules(
                size,
                readPalette(config, logger),
                config.getString("fortress.crystal.base", "OBSIDIAN"),
                config.getInt("fortress.crystal.clearance-radius", 1),
                config.getInt("fortress.crystal.clearance-height", 3));

        this.ordered = byStrength(buildRules.allowance().keySet());
    }

    /**
     * The whole palette, best first.
     *
     * "Best" is not my opinion: for a fortress, a good block is one that takes a long time
     * to break and survives an explosion. So they are ranked by <b>blast resistance, then
     * hardness</b> — the game's own numbers. Change the palette and the hotbar and the
     * inventory re-sort themselves; nobody has to maintain a second list that silently
     * drifts out of date.
     */
    private static List<String> byStrength(Collection<String> palette) {
        return palette.stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble(Material::getBlastResistance).reversed()
                        .thenComparing(Comparator.comparingDouble(Material::getHardness).reversed())
                        .thenComparing(Material::name))
                .map(Material::name)
                .toList();
    }

    /** Every allowed block, hardest first. The first eight go in the hotbar. */
    public List<String> paletteByStrength() {
        return ordered;
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
            // Some blocks have no item at all (REDSTONE_WIRE is placed with REDSTONE). If we
            // cannot even hand it to a builder, it does not belong in the palette — and
            // finding that out at boot beats finding it out when a menu dies mid-render.
            if (PlacingItems.of(material) == null) {
                logger.warning("Palette: '" + key + "' cannot be given to a player — "
                        + "no item places it. Skipped. (Add it to PlacingItems if it should work.)");
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

    /** How long a team has to look round three fortresses and agree on one. */
    public int voteSeconds() {
        return voteSeconds;
    }

    /**
     * How much the End Crystal takes before it breaks.
     *
     * The length of the endgame, in one number. Vanilla gives a crystal ONE hit point; at
     * that value a fortress is a formality, and the first arrow across the map ends the
     * match. Too high and nobody ever finishes one and every game goes to kills.
     */
    /** How long you lie dead before you are put back on your pad. */
    public int respawnSeconds() {
        return respawnSeconds;
    }

    /** How long you cannot be hurt after coming back — and it ends the moment you swing. */
    public int spawnProtectionSeconds() {
        return spawnProtectionSeconds;
    }

    public int crystalHealth() {
        return crystalHealth;
    }

    /** How long one attacker's blow keeps the crystal to itself. See the config for why. */
    public long crystalHitCooldownTicks() {
        return crystalHitCooldownTicks;
    }

    /** What each kind of blow is worth against a crystal. */
    public CrystalRules crystalRules() {
        return crystalRules;
    }

    public String buildWorld() {
        return buildWorld;
    }

    /** How far a build room is rendered. It is a 50-block cube in the void: 5 chunks is plenty. */
    public int buildViewDistance() {
        return buildViewDistance;
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

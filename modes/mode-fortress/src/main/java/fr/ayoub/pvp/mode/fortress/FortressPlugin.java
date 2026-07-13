package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.mode.fortress.build.BuildListener;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.build.VoidGenerator;
import fr.ayoub.pvp.mode.fortress.map.FortressMapBuilder;
import fr.ayoub.pvp.mode.fortress.match.CrystalListener;
import fr.ayoub.pvp.mode.fortress.match.CrystalRegistry;
import fr.ayoub.pvp.mode.fortress.match.VoteRegistry;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * The Fortress plugin.
 *
 * Note what it asks the engine for, and what it does not. It asks for the database, the
 * queues and the lobby — all through the SPI, all things any mode could want. It does
 * <b>not</b> ask the engine to know what a fortress is: the table, the build world, the
 * menus and the rules are all in this jar. Delete it and the engine does not notice.
 */
public final class FortressPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        FortressConfig config = new FortressConfig(getConfig(), getLogger());

        if (config.buildRules().allowance().isEmpty()) {
            getLogger().severe("The palette is empty — nobody could build anything. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Our own table, in our own jar, run by the engine's migration machinery.
        PvPEngineApi.storage().migrate(
                "fortress",
                getClassLoader(),
                "/db/fortress/",
                List.of("V1__fortresses.sql"));

        FortressRepository fortresses =
                new FortressRepository(PvPEngineApi.storage().dataSource());

        // Nothing reads the stored "playable" flag directly. The rules can change under a
        // fortress that was saved months ago, so the library re-checks every read against
        // the rules as they are now — and repairs the row on its way past.
        FortressLibrary library = new FortressLibrary(fortresses, config.buildRules());

        BuildZoneService zones = new BuildZoneService(this, config, fortresses);
        zones.createWorld();

        getServer().getPluginManager().registerEvents(new BuildListener(zones, config), this);

        // The match world. Its own — Fortress is destructible, and the engine's void arenas
        // are not: no ore to mine, no floor to dig, nothing to loot.
        World arenas = ensureArenaWorld();
        if (arenas != null) {
            buildMapIfMissing(arenas, config);
        }

        // One listener for the whole server; one handler per match. The registry is the join
        // between them: given the crystal that was hit, whose match is it?
        CrystalRegistry crystals = new CrystalRegistry();
        getServer().getPluginManager().registerEvents(new CrystalListener(crystals), this);

        VoteRegistry votes = new VoteRegistry();
        getServer().getPluginManager().registerEvents(votes, this);

        PvPEngineApi.modes().register(
                new FortressMode(config, zones, fortresses, library, crystals, votes));

        getLogger().info("Fortress ready — " + config.fortressSize() + "³ fortresses, "
                + config.buildRules().allowance().size() + " block types, "
                + config.zoneCount() + " build zones.");
    }

    private World ensureArenaWorld() {
        String name = getConfig().getString("map.world", "fortress");

        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }

        getLogger().info("Creating the fortress match world '" + name + "'…");
        world = new WorldCreator(name)
                .generator(new VoidGenerator())   // we build every block of it ourselves
                .environment(World.Environment.NORMAL)
                .createWorld();

        if (world == null) {
            getLogger().severe("Could not create the fortress world. Fortress will have no map.");
            return null;
        }

        // Mobs are placed by the map, deliberately, where the map wants the danger to be.
        // Natural spawning would fill the island with skeletons within a minute.
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setTime(6000);
        world.setDifficulty(Difficulty.NORMAL);

        return world;
    }

    /**
     * Build the development map, once, if nobody has put a real one there.
     *
     * A designer's map drops into {@code plugins/PvPEngine/maps/} with the same
     * {@code markers:} in its map.yml, and this never runs again — it only ever fills a gap,
     * it never overwrites.
     */
    private void buildMapIfMissing(World world, FortressConfig config) {
        File maps = new File(getDataFolder().getParentFile(), "PvPEngine/maps");
        File[] existing = maps.listFiles((dir, name) -> name.matches("fortress-\\d+\\.yml"));

        if (existing != null && existing.length > 0) {
            if (upToDate(existing)) {
                return;
            }
            // The map's SHAPE changed — version 2 added the voting plains and had to raise
            // the ceiling to hold them. Keeping the old files would teleport players to a
            // plain that was never built. Rebuild, and say so.
            getLogger().warning("The fortress map is out of date (the shape changed). Rebuilding it.");
            for (File file : existing) {
                file.delete();
            }
        }

        int instances = Math.max(1, getConfig().getInt("map.instances", 2));
        getLogger().info("No fortress map found — building " + instances + " …");

        int blocks = new FortressMapBuilder(this, config).build(world, instances);

        getLogger().info("Fortress map built: " + blocks + " blocks, " + instances
                + " instance(s). Restart or /pvpadmin reload to load them.");
    }

    /**
     * Is every map <b>we generated</b> from the current shape?
     *
     * The rule is: we only ever rebuild our own. A map a designer hands over must never be
     * touched, whatever we change here.
     *
     * "Ours" means the file is called {@code fortress-<number>.yml} — which is exactly and
     * only what the generator writes. Version 1 shipped without a version key, so a file of
     * ours with no version is version 1, and out of date. A designer's map is simply not
     * called that, and never matches.
     */
    private boolean upToDate(File[] maps) {
        for (File file : maps) {
            if (!file.getName().matches("fortress-\\d+\\.yml")) {
                continue;   // not ours. Not ours to rebuild.
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            int version = yaml.getInt("fortress-map-version", 1);

            if (version < FortressMapBuilder.MAP_VERSION) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Nobody is left standing in a creative void when the server stops.
        getServer().getScheduler().cancelTasks(this);
    }
}

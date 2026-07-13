package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.mode.fortress.build.BuildListener;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.build.VoidGenerator;
import fr.ayoub.pvp.mode.fortress.map.FortressMapBuilder;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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

        PvPEngineApi.modes().register(new FortressMode(config, zones, fortresses, library));

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
        File[] existing = maps.listFiles((dir, name) -> name.startsWith("fortress-"));

        if (existing != null && existing.length > 0) {
            return;
        }

        int instances = Math.max(1, getConfig().getInt("map.instances", 2));
        getLogger().info("No fortress map found — building " + instances + " …");

        int blocks = new FortressMapBuilder(this, config).build(world, instances);

        getLogger().info("Fortress map built: " + blocks + " blocks, " + instances
                + " instance(s). Restart or /pvpadmin reload to load them.");
    }

    @Override
    public void onDisable() {
        // Nobody is left standing in a creative void when the server stops.
        getServer().getScheduler().cancelTasks(this);
    }
}

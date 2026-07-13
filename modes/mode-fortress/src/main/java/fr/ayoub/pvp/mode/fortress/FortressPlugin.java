package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.mode.fortress.build.BuildListener;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import org.bukkit.plugin.java.JavaPlugin;

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

        PvPEngineApi.modes().register(new FortressMode(config, zones, fortresses, library));

        getLogger().info("Fortress ready — " + config.fortressSize() + "³ fortresses, "
                + config.buildRules().allowance().size() + " block types, "
                + config.zoneCount() + " build zones.");
    }

    @Override
    public void onDisable() {
        // Nobody is left standing in a creative void when the server stops.
        getServer().getScheduler().cancelTasks(this);
    }
}

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

        // Nothing on this map may be visible from anything else on it: not the enemy's voting
        // plain, not the map you are about to fight on, not the match next door. All three have
        // been broken at least once by a number changed elsewhere, so the geometry is checked
        // here, against the fortress size THIS server configured — a bigger fortress makes the
        // voting plain deeper and eats the gap behind it.
        try {
            FortressMapBuilder.layout(config.fortressSize());
        } catch (IllegalArgumentException e) {
            getLogger().severe("The fortress map geometry does not hold: " + e.getMessage());
            getLogger().severe("Lower 'fortress.size', or move the islands further apart. Disabling.");
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
        //
        // Whether the map is out of date is decided BEFORE the world exists, because the answer
        // may be "throw the world away". See wipeIfStale.
        wipeIfStale();

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
    /**
     * The map's shape changed. Throw the old world away — do not build on top of it.
     *
     * <p>This used to delete the map files and rebuild, and leave <b>every block of the old
     * layout standing</b>. It did not show while the islands only ever grew in place. Then the
     * spacing doubled and the voting plains moved, and the new map was built <em>beside</em> the
     * old one: players fought on the new island with the previous version's voting plain still
     * hanging in the sky, and abandoned islands sitting where the new ones were not.
     *
     * <p>There is no way to clear "the old volume" — we no longer know what the old constants
     * were, and the next change will have different ones again. The only thing that is correct
     * for an <b>arbitrary</b> layout change is to delete the world and generate it fresh. It
     * costs nothing to do: this world holds no player data and no designer's work. Every block
     * in it was put there by the code in this jar.
     *
     * <p>Which is exactly why it stops if it finds a map that is <b>not ours</b> living in it.
     * We only ever rebuild what we generated.
     */
    private void wipeIfStale() {
        File[] ours = ourMaps();
        if (ours.length == 0 || upToDate(ours)) {
            return;
        }

        String name = getConfig().getString("map.world", "fortress");

        if (hasForeignMapIn(name)) {
            getLogger().warning("The fortress map is out of date, but world '" + name
                    + "' also holds a map we did not generate. Leaving the world alone —"
                    + " the old layout may still be visible. Move that map to its own world.");
            return;
        }

        getLogger().warning("The fortress map is out of date (the shape changed)."
                + " Deleting world '" + name + "' and generating it again.");

        for (File file : ours) {
            file.delete();
        }

        World world = Bukkit.getWorld(name);
        if (world != null && !Bukkit.unloadWorld(world, false)) {
            getLogger().severe("Could not unload '" + name + "'. The old map will still be there.");
            return;
        }

        delete(new File(Bukkit.getWorldContainer(), name));
    }

    /** Is there a map in this world that we did not write? Then it is not ours to delete. */
    private boolean hasForeignMapIn(String world) {
        File maps = new File(getDataFolder().getParentFile(), "PvPEngine/maps");
        File[] all = maps.listFiles((dir, name) -> name.endsWith(".yml"));
        if (all == null) {
            return false;
        }

        for (File file : all) {
            if (file.getName().matches("fortress-\\d+\\.yml")) {
                continue;   // ours
            }
            if (world.equals(YamlConfiguration.loadConfiguration(file).getString("world"))) {
                return true;
            }
        }
        return false;
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

    private File[] ourMaps() {
        File maps = new File(getDataFolder().getParentFile(), "PvPEngine/maps");
        File[] ours = maps.listFiles((dir, name) -> name.matches("fortress-\\d+\\.yml"));
        return ours == null ? new File[0] : ours;
    }

    private void buildMapIfMissing(World world, FortressConfig config) {
        int wanted = Math.max(1, getConfig().getInt("map.instances", 2));
        int have = ourMaps().length;   // wipeIfStale has already run: what is left is current

        if (have >= wanted) {
            return;
        }

        // Raising map.instances has to actually GIVE you the islands. Building "only if
        // nothing exists" quietly ignored the new number: the config said four concurrent
        // matches and the server had two, and the third pair of players simply waited in a
        // queue that never moved, with nothing in the log to say why.
        //
        // The ones that already exist are left exactly as they are. Only the new indices are
        // built — an island somebody is fighting on must not be rebuilt under them.
        getLogger().info("Building fortress instances " + (have + 1) + "–" + wanted + " …");

        int blocks = new FortressMapBuilder(this, config).build(world, have, wanted);

        getLogger().info("Fortress map built: " + blocks + " blocks, " + (wanted - have)
                + " new instance(s), " + wanted + " in total.");
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

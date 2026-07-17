package fr.ayoub.pvp.core;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.core.admin.AdminCommand;
import fr.ayoub.pvp.core.admin.MapEditor;
import fr.ayoub.pvp.core.arena.ArenaLoader;
import fr.ayoub.pvp.core.arena.ArenaResetService;
import fr.ayoub.pvp.core.arena.ArenaService;
import fr.ayoub.pvp.core.arena.WallListener;
import fr.ayoub.pvp.core.lobby.CoreLobby;
import fr.ayoub.pvp.core.lobby.HotbarItems;
import fr.ayoub.pvp.core.lobby.LobbyListener;
import fr.ayoub.pvp.core.lobby.LobbyService;
import fr.ayoub.pvp.core.match.GameModeRegistry;
import fr.ayoub.pvp.core.match.MatchListener;
import fr.ayoub.pvp.core.match.MatchService;
import fr.ayoub.pvp.core.party.PartyService;
import fr.ayoub.pvp.core.queue.QueueService;
import fr.ayoub.pvp.core.storage.CoreStorage;
import fr.ayoub.pvp.core.ui.MenuListener;
import fr.ayoub.pvp.core.ui.Sidebar;
import fr.ayoub.pvp.core.world.VoidChunkGenerator;
import fr.ayoub.pvp.core.world.WorldSetup;
import fr.ayoub.pvp.core.world.WorldTuning;
import fr.ayoub.pvp.storage.DataSourceFactory;
import fr.ayoub.pvp.storage.DatabaseConfig;
import fr.ayoub.pvp.storage.MigrationRunner;
import fr.ayoub.pvp.storage.PlayerRepository;
import fr.ayoub.pvp.storage.RatingRepository;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the engine.
 *
 *   M0 — config + database          (done)
 *   M1 — lobby, hotbar, menus       (done)
 *   M2 — arenas + invisible walls   (done)
 *   M3 — queue + match + duel mode  (done)
 *   M4 — Elo + leaderboard
 */
public final class PvPEnginePlugin extends JavaPlugin {

    public static final int STARTING_RATING = 1000;

    private DataSource dataSource;
    private ExecutorService asyncExecutor;

    private PlayerRepository playerRepository;
    private RatingRepository ratingRepository;

    private ArenaService arenaService;
    private ArenaResetService resetService;
    private LobbyService lobbyService;
    private HotbarItems hotbarItems;
    private GameModeRegistry gameModeRegistry;
    private MatchService matchService;
    private MapEditor mapEditor;
    private QueueService queueService;
    private PartyService partyService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            dataSource = DataSourceFactory.create(readDatabaseConfig());
            new MigrationRunner(dataSource).migrate();
        } catch (RuntimeException e) {
            getLogger().severe("Could not reach the database: " + e.getMessage());
            getLogger().severe("Check the 'database' section of config.yml. Disabling PvP Engine.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Every database call goes through here — never on the main thread.
        asyncExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "pvp-engine-db");
            thread.setDaemon(true);
            return thread;
        });

        playerRepository = new PlayerRepository(dataSource);
        ratingRepository = new RatingRepository(dataSource);

        // The void world must exist before maps (which reference it) are loaded.
        ensurePvpWorld();

        arenaService = new ArenaService();
        arenaService.load(ArenaLoader.loadAll(this));

        // A fresh server has a void world and no map at all. Rather than force an admin
        // to log in and run /pvpadmin setup before anyone can play, build the development
        // map ourselves — but only if there is no map yet, so we never touch a real one.
        autoSetupIfEmpty();

        hotbarItems = new HotbarItems(this);
        lobbyService = new LobbyService(readLobbySpawn(), hotbarItems, arenaService);

        gameModeRegistry = new GameModeRegistry(getLogger(), getConfig().getConfigurationSection("modes"));

        // Mode plugins can now register — and reach the database (for the ones with something
        // to remember) and the queues (for the ones with a screen of their own). A mode owns
        // its tables and its menus; the engine never grows either.
        //
        // The lobby handle resolves its services lazily, so it is safe to hand out here:
        // mode plugins only enable once this method has returned.
        PvPEngineApi.init(
                gameModeRegistry,
                new CoreStorage(dataSource, asyncExecutor),
                new CoreLobby(this));

        matchService = new MatchService(this);
        queueService = new QueueService(this);
        partyService = new PartyService(this, getConfig().getInt("party.max-size", 5));

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this, lobbyService, hotbarItems), this);
        getServer().getPluginManager().registerEvents(new WallListener(arenaService), this);
        getServer().getPluginManager().registerEvents(new MatchListener(matchService), this);

        mapEditor = new MapEditor(this);
        getServer().getPluginManager().registerEvents(mapEditor, this);

        AdminCommand admin = new AdminCommand(this, arenaService);
        getCommand("pvpadmin").setExecutor(admin);
        getCommand("pvpadmin").setTabCompleter(admin);

        // One tick a second: expire invites, form matches, refresh sidebars.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            partyService.tick();
            queueService.tick();
            matchService.remindSpectators();
            Bukkit.getOnlinePlayers().forEach(player -> Sidebar.update(this, player));
        }, 20L, 20L);

        Bukkit.getOnlinePlayers().forEach(lobbyService::send);

        // A mode plugin enables AFTER us, and it may bring maps of its own — Fortress builds
        // its island and writes a map.yml on first start. Scheduling a reload for the first
        // tick means we see them: by then every plugin has enabled. Without this, a mode's
        // own map is invisible until the next restart, and the mode looks broken on the very
        // first boot.
        Bukkit.getScheduler().runTask(this, () -> {
            // A hand-made arena lives in its own world (elarion, the Chunkity arenas), and Paper
            // only auto-loads the main world and our mode worlds. So load any world a map.yml names
            // before reading the maps — otherwise the map is skipped for "world not loaded" and the
            // admin has to load it by hand after every restart.
            autoLoadMapWorlds();

            arenaService.load(ArenaLoader.loadAll(this));
            getLogger().info("Maps loaded: " + arenaService.all().size());

            // Now that every world exists — ours and every mode's — pull the one lever that
            // decides whether a hundred matches fit on this box: how far each of them is
            // rendered. See WorldTuning.
            WorldTuning.apply(this, lobbyService.spawn().getWorld(), arenaService.all());

            // Photograph every arena that says it can be wrecked — or, if it has been photographed
            // before, put it back to that photograph before anybody can queue. This is what makes a
            // server that was KILLED rather than stopped wake up on a clean map instead of on the
            // wreckage of the match it died in. See ArenaResetService.
            resetService = new ArenaResetService(this);
            resetService.prepare(arenaService.all());
        });

        getLogger().info("PvP Engine enabled — " + arenaService.all().size() + " map(s).");
    }

    @Override
    public void onDisable() {
        if (matchService != null) {
            matchService.abortAll();   // nobody stays stranded in an arena
        }
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null) {
            DataSourceFactory.close(dataSource);
        }
        getLogger().info("PvP Engine disabled.");
    }

    /** Tell Bukkit that our world is generated by {@link VoidChunkGenerator}. */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }

    /** Creates the void PvP world on first start, and keeps it calm. */
    private void ensurePvpWorld() {
        String name = getConfig().getString("world.name", "pvp");

        World world = Bukkit.getWorld(name);
        if (world == null) {
            if (!getConfig().getBoolean("world.auto-create", true)) {
                return;
            }
            getLogger().info("Creating the void world '" + name + "' …");
            world = new WorldCreator(name)
                    .generator(new VoidChunkGenerator())
                    .environment(World.Environment.NORMAL)
                    .createWorld();
        }
        if (world == null) {
            getLogger().severe("Could not create the world '" + name + "'.");
            return;
        }

        // A PvP world does not need weather, night, mobs or hunger surprises.
        world.setDifficulty(Difficulty.NORMAL);        // PEACEFUL would cancel all damage
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setTime(6000);                            // noon
        world.setStorm(false);
    }

    /**
     * Builds the development map on a brand-new server, so it is playable straight after
     * install with nobody having to type anything.
     *
     * Only runs when there is <b>no map at all</b>. As soon as you have real, designed
     * maps, this never fires again — and you can disable it outright with
     * {@code world.auto-setup-arenas: 0}.
     */
    private void autoSetupIfEmpty() {
        int arenas = getConfig().getInt("world.auto-setup-arenas", 0);

        if (arenas <= 0 || !arenaService.all().isEmpty()) {
            return;
        }

        World world = Bukkit.getWorld(getConfig().getString("world.name", "pvp"));
        if (world == null) {
            return;
        }

        getLogger().info("No map found — building the development map (" + arenas + " arenas)…");
        WorldSetup.Result result = WorldSetup.build(this, world, arenas);

        // Point the lobby at the platform we just built (read again below).
        getConfig().set("lobby.world", world.getName());
        getConfig().set("lobby.x", 0.5);
        getConfig().set("lobby.y", (double) WorldSetup.LOBBY_Y + 1);
        getConfig().set("lobby.z", 0.5);
        saveConfig();

        arenaService.load(ArenaLoader.loadAll(this));

        getLogger().info("Development map built: " + result.blocksPlaced() + " blocks, "
                + result.arenas() + " arena(s) → " + result.arenas() + " simultaneous matches.");
    }

    private DatabaseConfig readDatabaseConfig() {
        ConfigurationSection section = getConfig().getConfigurationSection("database");
        if (section == null) {
            throw new IllegalStateException("missing 'database' section in config.yml");
        }
        return new DatabaseConfig(
                section.getString("host", "localhost"),
                section.getInt("port", 3306),
                section.getString("name", "pvp_engine"),
                section.getString("user", "pvp"),
                section.getString("password", ""),
                section.getInt("pool-size", 10));
    }

    private Location readLobbySpawn() {
        ConfigurationSection section = getConfig().getConfigurationSection("lobby");
        String worldName = section != null ? section.getString("world", "world") : "world";

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
            getLogger().warning("Lobby world '" + worldName + "' not found, using '" + world.getName() + "'.");
        }
        if (section == null) {
            return world.getSpawnLocation();
        }

        return new Location(
                world,
                section.getDouble("x", 0.5),
                section.getDouble("y", 100.0),
                section.getDouble("z", 0.5),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0));
    }

    public ExecutorService async() {
        return asyncExecutor;
    }

    public PlayerRepository players() {
        return playerRepository;
    }

    public RatingRepository ratings() {
        return ratingRepository;
    }

    public MapEditor mapEditor() {
        return mapEditor;
    }

    /**
     * Load a world by name if its folder is there — wherever "there" is.
     *
     * <p>Paper 26.x keeps extra worlds under {@code world/dimensions/minecraft/<name>/}, but a world
     * you have just dropped in sits at the top level until Paper migrates it on first load. So both
     * places are checked; either counts. Returns the world, or null if there is no folder to load.
     */
    public World loadWorldByName(String name) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }

        java.io.File topLevel = new java.io.File(getServer().getWorldContainer(), name);
        java.io.File migrated = new java.io.File(
                getServer().getWorldContainer(), "world/dimensions/minecraft/" + name);
        if (!topLevel.isDirectory() && !migrated.isDirectory()) {
            return null;
        }
        return new WorldCreator(name).createWorld();
    }

    /** Load every world a {@code map.yml} references, so the maps are not skipped at boot. */
    private void autoLoadMapWorlds() {
        java.io.File maps = new java.io.File(getDataFolder(), "maps");
        java.io.File[] files = maps.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }

        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (java.io.File file : files) {
            String world = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(file).getString("world");
            if (world != null) {
                wanted.add(world);
            }
        }

        for (String name : wanted) {
            if (Bukkit.getWorld(name) != null) {
                continue;
            }
            if (loadWorldByName(name) != null) {
                getLogger().info("Loaded map world '" + name + "'.");
            } else {
                getLogger().warning("A map wants world '" + name + "' but its folder is missing.");
            }
        }
    }

    public ArenaResetService resets() {
        return resetService;
    }

    public ArenaService arenas() {
        return arenaService;
    }

    public LobbyService lobby() {
        return lobbyService;
    }

    public HotbarItems hotbar() {
        return hotbarItems;
    }

    public GameModeRegistry modes() {
        return gameModeRegistry;
    }

    public MatchService matches() {
        return matchService;
    }

    public QueueService queue() {
        return queueService;
    }

    public PartyService parties() {
        return partyService;
    }
}

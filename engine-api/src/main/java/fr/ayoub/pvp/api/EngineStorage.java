package fr.ayoub.pvp.api;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The database, opened to a game mode.
 *
 * A mode that needs to remember things — Fortress remembers what people built — <b>owns its
 * own tables</b>. The engine must not grow a "fortresses" table: the day it does, every new
 * mode becomes an engine change, and the whole point of this SPI is gone.
 *
 * <pre>
 * public void onEnable() {
 *     PvPEngineApi.storage().migrate("fortress", getClassLoader(),
 *                                    "/db/fortress/", List.of("V1__fortresses.sql"));
 *     PvPEngineApi.modes().register(new FortressMode(...));
 * }
 * </pre>
 *
 * Keep the SQL portable — it must run on MySQL and on H2, which is what tests use.
 */
public interface EngineStorage {

    /**
     * The engine's connection pool. Shared, already configured.
     *
     * <b>Never touch it from the main thread</b> — JDBC blocks, and a blocked main thread is
     * a frozen server. Go through {@link #async()}.
     */
    DataSource dataSource();

    /**
     * Create or update this mode's tables. Safe to call on every start: each version runs
     * once, in order, one transaction apiece, and is remembered.
     *
     * @param namespace the mode id, so two modes can both ship a "V1"
     * @param loader    your plugin's class loader — your jar is where the .sql files are
     * @param path      the resource folder inside it, e.g. {@code /db/fortress/}
     * @param versions  the files, in the order they must run
     */
    void migrate(String namespace, ClassLoader loader, String path, List<String> versions);

    /** The engine's database thread pool. Come back to the main thread before touching the world. */
    Executor async();
}

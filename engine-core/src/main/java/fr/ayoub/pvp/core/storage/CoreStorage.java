package fr.ayoub.pvp.core.storage;

import fr.ayoub.pvp.api.EngineStorage;
import fr.ayoub.pvp.storage.MigrationRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.Executor;

/** The engine's side of {@link EngineStorage}: it hands a mode the pool it already has. */
public final class CoreStorage implements EngineStorage {

    private final DataSource dataSource;
    private final Executor async;

    public CoreStorage(DataSource dataSource, Executor async) {
        this.dataSource = dataSource;
        this.async = async;
    }

    @Override
    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void migrate(String namespace, ClassLoader loader, String path, List<String> versions) {
        new MigrationRunner(dataSource).migrate(namespace, loader, path, versions);
    }

    @Override
    public Executor async() {
        return async;
    }
}

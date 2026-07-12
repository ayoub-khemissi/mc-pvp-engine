package fr.ayoub.pvp.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Builds the connection pool.
 *
 * HikariCP and the MySQL driver are downloaded by Paper at startup
 * (see the "libraries" section of plugin.yml) — they are not shipped in the jar.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("pvp-engine");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(Math.min(2, config.poolSize()));
        hikari.setConnectionTimeout(10_000);
        hikari.setInitializationFailTimeout(10_000);
        return new HikariDataSource(hikari);
    }

    /** Closes the pool if it can be closed. */
    public static void close(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }
    }
}

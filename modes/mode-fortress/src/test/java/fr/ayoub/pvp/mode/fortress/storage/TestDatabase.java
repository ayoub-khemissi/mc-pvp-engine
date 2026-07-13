package fr.ayoub.pvp.mode.fortress.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.ayoub.pvp.storage.MigrationRunner;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * H2 in MySQL mode, in memory, with this mode's own migration applied.
 *
 * The point: the SQL Fortress ships is exercised for real, on a real SQL engine, on every
 * test run — no MySQL to install, no Docker. If a statement is not portable, it fails here
 * rather than on the production server.
 */
final class TestDatabase {

    private TestDatabase() {
    }

    static DataSource migrated() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:fortress-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(2);

        DataSource dataSource = new HikariDataSource(config);

        new MigrationRunner(dataSource).migrate(
                "fortress",
                TestDatabase.class.getClassLoader(),
                "/db/fortress/",
                List.of("V1__fortresses.sql"));

        return dataSource;
    }
}

package fr.ayoub.pvp.storage;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * An in-memory SQL database for tests.
 *
 * H2 in MySQL-compatibility mode: real SQL, real constraints, no MySQL server
 * and no Docker needed to run `gradlew test`.
 */
final class TestDatabase {

    private TestDatabase() {
    }

    /** A brand new, empty database (unique per call, so tests never share state). */
    static DataSource empty() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pvp_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    /** A database with the schema already applied. */
    static DataSource migrated() {
        DataSource ds = empty();
        new MigrationRunner(ds).migrate();
        return ds;
    }

    static int countRows(DataSource ds, String table) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException("query failed on table " + table, e);
        }
    }
}

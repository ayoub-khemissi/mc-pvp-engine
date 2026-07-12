package fr.ayoub.pvp.storage;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationRunnerTest {

    @Test
    void createsTheSchema() {
        DataSource ds = TestDatabase.empty();

        new MigrationRunner(ds).migrate();

        assertDoesNotThrow(() -> TestDatabase.countRows(ds, "players"));
        assertDoesNotThrow(() -> TestDatabase.countRows(ds, "ratings"));
    }

    @Test
    void recordsWhichMigrationsWereApplied() {
        DataSource ds = TestDatabase.empty();

        new MigrationRunner(ds).migrate();

        assertEquals(1, TestDatabase.countRows(ds, "schema_migrations"));
    }

    @Test
    void runningTwiceIsSafe() {
        DataSource ds = TestDatabase.empty();

        new MigrationRunner(ds).migrate();
        assertDoesNotThrow(() -> new MigrationRunner(ds).migrate());

        // still applied exactly once — no duplicate, no error
        assertEquals(1, TestDatabase.countRows(ds, "schema_migrations"));
    }
}

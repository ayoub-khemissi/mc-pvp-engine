package fr.ayoub.pvp.storage;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the SQL migrations, once each, and remembers which ones ran.
 *
 * Keep the SQL portable (it must run on both MySQL and H2, which is what the
 * tests use): no ENUM, no ON DUPLICATE KEY, no engine-specific syntax.
 */
public final class MigrationRunner {

    /** Ordered. Add the next file here when you add a migration. */
    private static final List<String> MIGRATIONS = List.of(
            "V1__init.sql"
    );

    private static final String PATH = "/db/migration/";

    private final DataSource dataSource;

    public MigrationRunner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** The engine's own tables. */
    public void migrate() {
        migrate("", MigrationRunner.class.getClassLoader(), PATH, MIGRATIONS);
    }

    /**
     * A <b>game mode's</b> tables, from that mode's own jar.
     *
     * A mode owns its schema: the engine must not grow a "fortresses" table, or every new
     * mode becomes an engine change. It gets the same guarantees as the engine's own
     * migrations — applied once each, in order, one transaction apiece — and its versions
     * are namespaced in {@code schema_migrations} so two modes can both ship a "V1".
     *
     * @param namespace the mode id, e.g. "fortress". Empty for the engine.
     * @param loader    the mode plugin's class loader — its jar is where the .sql files are
     * @param path      resource folder, e.g. {@code /db/fortress/}
     * @param versions  file names, in the order they must run
     */
    public void migrate(String namespace, ClassLoader loader, String path, List<String> versions) {
        try (Connection connection = dataSource.getConnection()) {
            createHistoryTable(connection);
            Set<String> applied = appliedVersions(connection);

            for (String version : versions) {
                String key = namespace.isBlank() ? version : namespace + "/" + version;
                if (!applied.contains(key)) {
                    apply(connection, key, read(loader, path + version, version));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("database migration failed", e);
        }
    }

    private void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version    VARCHAR(64) NOT NULL,
                        applied_at TIMESTAMP   NOT NULL,
                        PRIMARY KEY (version)
                    )
                    """);
        }
    }

    private Set<String> appliedVersions(Connection connection) throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_migrations")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
            }
        }
        return versions;
    }

    /** One migration = one transaction. It either fully applies, or not at all. */
    private void apply(Connection connection, String version, String script) throws SQLException {
        List<String> statements = split(script);

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)")) {
                ps.setString(1, version);
                ps.setTimestamp(2, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("migration " + version + " failed", e);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static List<String> split(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.strip().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String sql = part.strip();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }

    private static String read(ClassLoader loader, String resource, String version) {
        // A ClassLoader wants no leading slash; a Class does. Accept both.
        String path = resource.startsWith("/") ? resource.substring(1) : resource;

        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("migration file not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read migration " + version, e);
        }
    }
}

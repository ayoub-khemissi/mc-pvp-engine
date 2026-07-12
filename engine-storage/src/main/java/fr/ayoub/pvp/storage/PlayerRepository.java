package fr.ayoub.pvp.storage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the {@code players} table.
 *
 * Blocking JDBC — callers must never run this on the Bukkit main thread.
 * (engine-core wraps these calls in an async executor.)
 */
public final class PlayerRepository {

    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Insert the player, or refresh their username and last-seen if they already exist. */
    public void upsert(UUID uuid, String username) {
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            // "UPDATE, and INSERT only if nothing was updated" keeps the SQL portable
            // (no ON DUPLICATE KEY / MERGE, which differ between MySQL and H2).
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE players SET username = ?, last_seen = ? WHERE uuid = ?")) {
                update.setString(1, username);
                update.setTimestamp(2, Timestamp.from(now));
                update.setString(3, uuid.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO players (uuid, username, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, username);
                insert.setTimestamp(3, Timestamp.from(now));
                insert.setTimestamp(4, Timestamp.from(now));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not save player " + uuid, e);
        }
    }

    public Optional<PlayerRecord> find(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT username, first_seen, last_seen FROM players WHERE uuid = ?")) {

            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerRecord(
                        uuid,
                        rs.getString("username"),
                        rs.getTimestamp("first_seen").toInstant(),
                        rs.getTimestamp("last_seen").toInstant()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not load player " + uuid, e);
        }
    }
}

package fr.ayoub.pvp.mode.fortress.storage;

import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.BlueprintCodec;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code fortresses} table — Fortress's own, not the engine's.
 *
 * Two invariants are kept here rather than left to callers, because getting them wrong puts
 * a player in a state they cannot get out of:
 * <ul>
 *   <li><b>Exactly one default.</b> A player with two defaults, or with fortresses and no
 *       default, has a match with nothing to fall back on. Setting one clears the others;
 *       deleting the default promotes another slot; the first fortress saved becomes the
 *       default by itself.</li>
 *   <li><b>One row per slot.</b> The primary key is (owner, slot), so "you cannot have four
 *       fortresses" is a fact of the schema.</li>
 * </ul>
 *
 * Blocking JDBC — never call from the Bukkit main thread. Go through the engine's async
 * executor ({@code PvPEngineApi.storage().async()}).
 */
public final class FortressRepository {

    private final DataSource dataSource;

    public FortressRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<SavedFortress> findAllFor(UUID owner) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT slot, name, blueprint, playable, is_default
                     FROM fortresses
                     WHERE owner_uuid = ?
                     ORDER BY slot
                     """)) {

            select.setString(1, owner.toString());

            List<SavedFortress> found = new ArrayList<>();
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    found.add(read(owner, rs));
                }
            }
            return found;

        } catch (SQLException e) {
            throw new IllegalStateException("could not load the fortresses of " + owner, e);
        }
    }

    /** The ones that may actually be taken into a match. A draft is not one of them. */
    public List<SavedFortress> findPlayableFor(UUID owner) {
        return findAllFor(owner).stream().filter(SavedFortress::playable).toList();
    }

    public Optional<SavedFortress> find(UUID owner, int slot) {
        return findAllFor(owner).stream()
                .filter(fortress -> fortress.slot() == slot)
                .findFirst();
    }

    public Optional<SavedFortress> findDefault(UUID owner) {
        return findAllFor(owner).stream()
                .filter(SavedFortress::isDefault)
                .findFirst();
    }

    /**
     * Write a slot, creating it or overwriting it.
     *
     * If it claims to be the default, every other slot stops being one. If the player had
     * no fortress at all, this one becomes the default whatever it says — nobody should end
     * up owning a fortress they cannot play.
     */
    public void save(SavedFortress fortress) {
        boolean makeDefault = fortress.isDefault() || !hasAny(fortress.owner());
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                if (makeDefault) {
                    clearDefaults(connection, fortress.owner());
                }
                write(connection, fortress.asDefault(makeDefault), now);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not save the fortress of " + fortress.owner(), e);
        }
    }

    public void setDefault(UUID owner, int slot) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                clearDefaults(connection, owner);

                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE fortresses SET is_default = TRUE, updated_at = ?
                        WHERE owner_uuid = ? AND slot = ?
                        """)) {
                    update.setTimestamp(1, Timestamp.from(Instant.now()));
                    update.setString(2, owner.toString());
                    update.setInt(3, slot);
                    update.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not set the default fortress of " + owner, e);
        }
    }

    /** Empty a slot. If it was the default, another slot inherits it. */
    public void delete(UUID owner, int slot) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM fortresses WHERE owner_uuid = ? AND slot = ?")) {

            delete.setString(1, owner.toString());
            delete.setInt(2, slot);
            delete.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("could not delete a fortress of " + owner, e);
        }

        if (findDefault(owner).isEmpty()) {
            findAllFor(owner).stream().findFirst()
                    .ifPresent(next -> setDefault(owner, next.slot()));
        }
    }

    // --- plumbing ---------------------------------------------------------------

    private boolean hasAny(UUID owner) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT 1 FROM fortresses WHERE owner_uuid = ? LIMIT 1")) {

            select.setString(1, owner.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not read the fortresses of " + owner, e);
        }
    }

    private static void clearDefaults(Connection connection, UUID owner) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE fortresses SET is_default = FALSE WHERE owner_uuid = ?")) {
            update.setString(1, owner.toString());
            update.executeUpdate();
        }
    }

    /** UPDATE, then INSERT if it hit nothing: portable, unlike ON DUPLICATE KEY. */
    private static void write(Connection connection, SavedFortress fortress, Instant now)
            throws SQLException {

        byte[] blueprint = BlueprintCodec.encode(fortress.blueprint());

        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE fortresses
                SET name = ?, blueprint = ?, size = ?, playable = ?, block_count = ?,
                    is_default = ?, updated_at = ?
                WHERE owner_uuid = ? AND slot = ?
                """)) {

            update.setString(1, fortress.name());
            update.setBytes(2, blueprint);
            update.setInt(3, fortress.size());
            update.setBoolean(4, fortress.playable());
            update.setInt(5, fortress.blockCount());
            update.setBoolean(6, fortress.isDefault());
            update.setTimestamp(7, Timestamp.from(now));
            update.setString(8, fortress.owner().toString());
            update.setInt(9, fortress.slot());

            if (update.executeUpdate() > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO fortresses
                    (owner_uuid, slot, name, blueprint, size, playable, block_count,
                     is_default, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {

            insert.setString(1, fortress.owner().toString());
            insert.setInt(2, fortress.slot());
            insert.setString(3, fortress.name());
            insert.setBytes(4, blueprint);
            insert.setInt(5, fortress.size());
            insert.setBoolean(6, fortress.playable());
            insert.setInt(7, fortress.blockCount());
            insert.setBoolean(8, fortress.isDefault());
            insert.setTimestamp(9, Timestamp.from(now));
            insert.setTimestamp(10, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    private static SavedFortress read(UUID owner, ResultSet rs) throws SQLException {
        Blueprint blueprint = BlueprintCodec.decode(rs.getBytes("blueprint"));

        return new SavedFortress(
                owner,
                rs.getInt("slot"),
                rs.getString("name"),
                blueprint,
                rs.getBoolean("playable"),
                rs.getBoolean("is_default"));
    }
}

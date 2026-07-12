package fr.ayoub.pvp.storage;

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
 * Reads and writes the {@code ratings} table.
 * Ratings are stored per (player, mode, format): your 1v1 rank is not your 3v3 rank.
 *
 * Blocking JDBC — never call from the Bukkit main thread.
 */
public final class RatingRepository {

    private final DataSource dataSource;

    public RatingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<RatingRow> find(UUID uuid, String modeId, String format) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT rating, peak_rating, games, wins, losses, streak
                     FROM ratings
                     WHERE uuid = ? AND mode_id = ? AND team_format = ?
                     """)) {

            select.setString(1, uuid.toString());
            select.setString(2, modeId);
            select.setString(3, format);

            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RatingRow(
                        rs.getInt("rating"),
                        rs.getInt("peak_rating"),
                        rs.getInt("games"),
                        rs.getInt("wins"),
                        rs.getInt("losses"),
                        rs.getInt("streak")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not load rating for " + uuid, e);
        }
    }

    public void save(UUID uuid, String modeId, String format, RatingRow row) {
        Instant now = Instant.now();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE ratings
                    SET rating = ?, peak_rating = ?, games = ?, wins = ?, losses = ?, streak = ?, updated_at = ?
                    WHERE uuid = ? AND mode_id = ? AND team_format = ?
                    """)) {
                update.setInt(1, row.rating());
                update.setInt(2, row.peakRating());
                update.setInt(3, row.games());
                update.setInt(4, row.wins());
                update.setInt(5, row.losses());
                update.setInt(6, row.streak());
                update.setTimestamp(7, Timestamp.from(now));
                update.setString(8, uuid.toString());
                update.setString(9, modeId);
                update.setString(10, format);

                if (update.executeUpdate() > 0) {
                    return;
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO ratings
                        (uuid, mode_id, team_format, rating, peak_rating, games, wins, losses, streak, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, uuid.toString());
                insert.setString(2, modeId);
                insert.setString(3, format);
                insert.setInt(4, row.rating());
                insert.setInt(5, row.peakRating());
                insert.setInt(6, row.games());
                insert.setInt(7, row.wins());
                insert.setInt(8, row.losses());
                insert.setInt(9, row.streak());
                insert.setTimestamp(10, Timestamp.from(now));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not save rating for " + uuid, e);
        }
    }

    /** Every rating a player has, across all modes and formats. Used by the profile. */
    public List<RatingEntry> findAllFor(UUID uuid) {
        List<RatingEntry> entries = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT mode_id, team_format, rating, peak_rating, games, wins, losses, streak
                     FROM ratings
                     WHERE uuid = ?
                     ORDER BY mode_id, team_format
                     """)) {

            select.setString(1, uuid.toString());

            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    entries.add(new RatingEntry(
                            rs.getString("mode_id"),
                            rs.getString("team_format"),
                            new RatingRow(
                                    rs.getInt("rating"),
                                    rs.getInt("peak_rating"),
                                    rs.getInt("games"),
                                    rs.getInt("wins"),
                                    rs.getInt("losses"),
                                    rs.getInt("streak"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not load ratings for " + uuid, e);
        }
        return entries;
    }

    /** Top players of a mode/format, best first. */
    public List<LeaderboardEntry> top(String modeId, String format, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT r.uuid, p.username, r.rating, r.games, r.wins, r.losses
                     FROM ratings r
                     JOIN players p ON p.uuid = r.uuid
                     WHERE r.mode_id = ? AND r.team_format = ?
                     ORDER BY r.rating DESC
                     LIMIT ?
                     """)) {

            select.setString(1, modeId);
            select.setString(2, format);
            select.setInt(3, limit);

            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getInt("rating"),
                            rs.getInt("games"),
                            rs.getInt("wins"),
                            rs.getInt("losses")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not load leaderboard for " + modeId + "/" + format, e);
        }
        return entries;
    }
}

package fr.ayoub.pvp.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RatingRepositoryTest {

    private static final String DUEL = "duel";
    private static final String ONE_V_ONE = "1v1";

    private DataSource ds;
    private PlayerRepository players;
    private RatingRepository ratings;

    @BeforeEach
    void setUp() {
        ds = TestDatabase.migrated();
        players = new PlayerRepository(ds);
        ratings = new RatingRepository(ds);
    }

    private UUID player(String name) {
        UUID id = UUID.randomUUID();
        players.upsert(id, name);
        return id;
    }

    @Test
    void anUnratedPlayerHasNoRow() {
        assertTrue(ratings.find(player("Nobody"), DUEL, ONE_V_ONE).isEmpty());
    }

    @Test
    void aRatingCanBeSavedAndReadBack() {
        UUID id = player("Ayoub");

        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1018, 1018, 1, 1, 0, 1));

        RatingRow row = ratings.find(id, DUEL, ONE_V_ONE).orElseThrow();
        assertEquals(1018, row.rating());
        assertEquals(1018, row.peakRating());
        assertEquals(1, row.games());
        assertEquals(1, row.wins());
        assertEquals(0, row.losses());
        assertEquals(1, row.streak());
    }

    @Test
    void savingAgainUpdatesInsteadOfDuplicating() {
        UUID id = player("Ayoub");

        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1018, 1018, 1, 1, 0, 1));
        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1002, 1018, 2, 1, 1, 0));

        RatingRow row = ratings.find(id, DUEL, ONE_V_ONE).orElseThrow();
        assertEquals(1002, row.rating());
        assertEquals(1018, row.peakRating(), "peak must be kept");
        assertEquals(2, row.games());
        assertEquals(1, TestDatabase.countRows(ds, "ratings"));
    }

    @Test
    void ratingsAreSeparatePerModeAndFormat() {
        UUID id = player("Ayoub");

        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1200, 1200, 10, 8, 2, 3));
        ratings.save(id, DUEL, "2v2", new RatingRow(900, 1000, 5, 1, 4, -2));

        assertEquals(1200, ratings.find(id, DUEL, ONE_V_ONE).orElseThrow().rating());
        assertEquals(900, ratings.find(id, DUEL, "2v2").orElseThrow().rating());
    }

    @Test
    void theLeaderboardIsSortedByRatingAndLimited() {
        UUID low = player("Low");
        UUID high = player("High");
        UUID mid = player("Mid");

        ratings.save(low, DUEL, ONE_V_ONE, new RatingRow(900, 900, 5, 1, 4, 0));
        ratings.save(high, DUEL, ONE_V_ONE, new RatingRow(1500, 1500, 20, 18, 2, 5));
        ratings.save(mid, DUEL, ONE_V_ONE, new RatingRow(1100, 1100, 9, 5, 4, 1));

        List<LeaderboardEntry> top = ratings.top(DUEL, ONE_V_ONE, 2);

        assertEquals(2, top.size(), "the limit must be respected");
        assertEquals("High", top.get(0).username());
        assertEquals(1500, top.get(0).rating());
        assertEquals("Mid", top.get(1).username());
    }

    @Test
    void theLeaderboardOnlyShowsTheRequestedMode() {
        UUID id = player("Ayoub");
        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1200, 1200, 10, 8, 2, 3));

        assertTrue(ratings.top("dodgeball", ONE_V_ONE, 10).isEmpty());
    }

    // --- the profile needs every rating a player has ---------------------------

    @Test
    void aPlayerWithNoGamesHasNoRatings() {
        assertTrue(ratings.findAllFor(player("Nobody")).isEmpty());
    }

    @Test
    void everyRatingOfAPlayerCanBeListed() {
        UUID id = player("Ayoub");
        ratings.save(id, DUEL, ONE_V_ONE, new RatingRow(1200, 1200, 10, 8, 2, 3));
        ratings.save(id, DUEL, "2v2", new RatingRow(900, 1000, 5, 1, 4, -2));
        ratings.save(id, "dodgeball", ONE_V_ONE, new RatingRow(1050, 1050, 2, 1, 1, 1));

        List<RatingEntry> all = ratings.findAllFor(id);

        assertEquals(3, all.size());
        assertTrue(all.stream().anyMatch(entry ->
                entry.modeId().equals(DUEL) && entry.format().equals("2v2") && entry.row().rating() == 900));
    }

    @Test
    void listingIsPerPlayer() {
        UUID mine = player("Ayoub");
        UUID theirs = player("Someone");
        ratings.save(mine, DUEL, ONE_V_ONE, new RatingRow(1200, 1200, 10, 8, 2, 3));

        assertTrue(ratings.findAllFor(theirs).isEmpty());
    }
}

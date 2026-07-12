package fr.ayoub.pvp.domain.rating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a match result does to a player's record. */
class RatingUpdaterTest {

    private final PlayerRating fresh = PlayerRating.initial(1000);

    @Test
    void aNewPlayerStartsClean() {
        assertEquals(1000, fresh.rating());
        assertEquals(1000, fresh.peak());
        assertEquals(0, fresh.games());
        assertEquals(0, fresh.wins());
        assertEquals(0, fresh.losses());
        assertEquals(0, fresh.streak());
    }

    @Test
    void aWinCountsAsAWin() {
        PlayerRating after = RatingUpdater.apply(fresh, 1020, Outcome.WIN);

        assertEquals(1020, after.rating());
        assertEquals(1, after.games());
        assertEquals(1, after.wins());
        assertEquals(0, after.losses());
    }

    @Test
    void aLossCountsAsALoss() {
        PlayerRating after = RatingUpdater.apply(fresh, 980, Outcome.LOSS);

        assertEquals(980, after.rating());
        assertEquals(1, after.games());
        assertEquals(0, after.wins());
        assertEquals(1, after.losses());
    }

    @Test
    void aDrawIsNeitherAWinNorALoss() {
        PlayerRating after = RatingUpdater.apply(fresh, 1000, Outcome.DRAW);

        assertEquals(1, after.games());
        assertEquals(0, after.wins());
        assertEquals(0, after.losses());
    }

    // --- streaks ---------------------------------------------------------------

    @Test
    void winsBuildAPositiveStreak() {
        PlayerRating one = RatingUpdater.apply(fresh, 1020, Outcome.WIN);
        PlayerRating two = RatingUpdater.apply(one, 1040, Outcome.WIN);
        PlayerRating three = RatingUpdater.apply(two, 1060, Outcome.WIN);

        assertEquals(3, three.streak());
    }

    @Test
    void aLossBreaksAWinStreakImmediately() {
        PlayerRating onAStreak = new PlayerRating(1100, 1100, 5, 5, 0, 5);

        PlayerRating after = RatingUpdater.apply(onAStreak, 1080, Outcome.LOSS);

        assertEquals(-1, after.streak(), "a loss must reset the streak, not merely decrement it");
    }

    @Test
    void lossesBuildANegativeStreak() {
        PlayerRating one = RatingUpdater.apply(fresh, 980, Outcome.LOSS);
        PlayerRating two = RatingUpdater.apply(one, 960, Outcome.LOSS);

        assertEquals(-2, two.streak());
    }

    @Test
    void aWinBreaksALosingStreakImmediately() {
        PlayerRating slumping = new PlayerRating(900, 1100, 5, 0, 5, -5);

        assertEquals(1, RatingUpdater.apply(slumping, 920, Outcome.WIN).streak());
    }

    @Test
    void aDrawEndsAnyStreak() {
        PlayerRating onAStreak = new PlayerRating(1100, 1100, 5, 5, 0, 5);

        assertEquals(0, RatingUpdater.apply(onAStreak, 1100, Outcome.DRAW).streak());
    }

    // --- peak ------------------------------------------------------------------

    @Test
    void thePeakFollowsANewHigh() {
        assertEquals(1020, RatingUpdater.apply(fresh, 1020, Outcome.WIN).peak());
    }

    @Test
    void thePeakIsNeverLost() {
        PlayerRating hadAGoodDay = new PlayerRating(1500, 1500, 20, 15, 5, 2);

        PlayerRating after = RatingUpdater.apply(hadAGoodDay, 1480, Outcome.LOSS);

        assertEquals(1480, after.rating());
        assertEquals(1500, after.peak(), "your best rating stays your best rating");
    }
}

package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest {

    private static final int RED = 0;
    private static final int BLUE = 1;

    @Test
    void aBestOfOneIsDecidedByASingleRound() {
        Series series = new Series(1, 2);

        assertEquals(1, series.roundsToWin());
        assertFalse(series.isDecided());

        series.recordRound(RED);

        assertTrue(series.isDecided());
        assertEquals(RED, series.winner().orElseThrow());
    }

    @Test
    void aBestOfThreeNeedsTwoWins() {
        Series series = new Series(3, 2);

        assertEquals(2, series.roundsToWin());

        series.recordRound(RED);
        assertFalse(series.isDecided(), "one win is not enough");
        assertEquals(1, series.wins(RED));
        assertEquals(0, series.wins(BLUE));

        series.recordRound(RED);
        assertTrue(series.isDecided());
        assertEquals(RED, series.winner().orElseThrow());
    }

    @Test
    void aSweepEndsTheSeriesEarly() {
        Series series = new Series(5, 2);

        series.recordRound(BLUE);
        series.recordRound(BLUE);
        series.recordRound(BLUE);

        assertTrue(series.isDecided());
        assertEquals(3, series.roundsPlayed(), "no dead rubber is played");
    }

    @Test
    void theScoreSeesSaws() {
        Series series = new Series(3, 2);

        series.recordRound(RED);
        series.recordRound(BLUE);

        assertFalse(series.isDecided());
        assertTrue(series.isMatchPoint(RED), "1-1 in a best-of-3: the next round decides it");
        assertTrue(series.isMatchPoint(BLUE));
        assertEquals(3, series.round(), "rounds are 1-based, so the decider is round 3");
    }

    @Test
    void matchPointIsOnlyForTheTeamOneWinAway() {
        Series series = new Series(5, 2);

        series.recordRound(RED);
        series.recordRound(RED);

        assertTrue(series.isMatchPoint(RED));
        assertFalse(series.isMatchPoint(BLUE));
    }

    @Test
    void thereIsNoWinnerUntilThereIsOne() {
        Series series = new Series(3, 2);

        assertTrue(series.winner().isEmpty());
        series.recordRound(RED);
        assertTrue(series.winner().isEmpty());
    }

    @Test
    void aDecidedSeriesCannotBePlayedFurther() {
        Series series = new Series(1, 2);
        series.recordRound(RED);

        assertThrows(IllegalStateException.class, () -> series.recordRound(BLUE));
    }

    @Test
    void anEvenBestOfIsRejected() {
        // 2 rounds can end 1-1: a series must always be able to produce a winner.
        assertThrows(IllegalArgumentException.class, () -> new Series(2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Series(0, 2));
    }

    @Test
    void anUnknownTeamIsRejected() {
        Series series = new Series(3, 2);

        assertThrows(IllegalArgumentException.class, () -> series.recordRound(2));
        assertThrows(IllegalArgumentException.class, () -> series.recordRound(-1));
    }

    @Test
    void itWorksWithMoreThanTwoTeams() {
        Series series = new Series(3, 3);

        series.recordRound(2);
        series.recordRound(2);

        assertEquals(2, series.winner().orElseThrow());
    }

    @Test
    void theScoreReadsAsALine() {
        Series series = new Series(3, 2);
        series.recordRound(RED);

        assertEquals("1 - 0", series.scoreline());
    }
}

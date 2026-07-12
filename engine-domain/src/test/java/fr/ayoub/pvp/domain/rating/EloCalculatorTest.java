package fr.ayoub.pvp.domain.rating;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloCalculatorTest {

    private final EloCalculator elo = new EloCalculator(KFactor.standard());

    // --- expectation -----------------------------------------------------------

    @Test
    void equalRatingsGiveFiftyFiftyExpectation() {
        assertEquals(0.5, EloCalculator.expected(1000, 1000), 1e-9);
    }

    @Test
    void theStrongerPlayerIsExpectedToWin() {
        assertTrue(EloCalculator.expected(1400, 1000) > 0.5);
        assertTrue(EloCalculator.expected(1000, 1400) < 0.5);
    }

    @Test
    void bothExpectationsSumToOne() {
        assertEquals(1.0,
                EloCalculator.expected(1234, 987) + EloCalculator.expected(987, 1234),
                1e-9);
    }

    // --- rating change ---------------------------------------------------------

    @Test
    void winningAgainstAnEqualOpponentGainsHalfOfK() {
        // 50 games -> K = 10 ; expected = 0.5 ; delta = 10 * (1 - 0.5) = 5
        RatingChange change = elo.compute(new RatingSnapshot(1000, 50), 1000, 1000, Outcome.WIN);

        assertEquals(1000, change.before());
        assertEquals(1005, change.after());
        assertEquals(5, change.delta());
    }

    @Test
    void losingAgainstAnEqualOpponentCostsHalfOfK() {
        assertEquals(-5, elo.compute(new RatingSnapshot(1000, 50), 1000, 1000, Outcome.LOSS).delta());
    }

    @Test
    void drawingAgainstAnEqualOpponentChangesNothing() {
        assertEquals(0, elo.compute(new RatingSnapshot(1000, 50), 1000, 1000, Outcome.DRAW).delta());
    }

    @Test
    void beatingAStrongOpponentIsWorthMoreThanBeatingAWeakOne() {
        int vsStronger = elo.compute(new RatingSnapshot(1000, 50), 1000, 1400, Outcome.WIN).delta();
        int vsWeaker = elo.compute(new RatingSnapshot(1000, 50), 1000, 600, Outcome.WIN).delta();

        assertTrue(vsStronger > vsWeaker,
                "beating a stronger opponent should give more rating (" + vsStronger + " vs " + vsWeaker + ")");
    }

    // --- K factor --------------------------------------------------------------

    @Test
    void newPlayersMoveFasterThanVeterans() {
        int rookie = elo.compute(new RatingSnapshot(1000, 0), 1000, 1000, Outcome.WIN).delta();
        int veteran = elo.compute(new RatingSnapshot(1000, 100), 1000, 1000, Outcome.WIN).delta();

        assertTrue(rookie > veteran, "placement games should move the rating faster");
    }

    @Test
    void standardKFactorDecreasesWithExperience() {
        KFactor k = KFactor.standard();
        assertEquals(40, k.forGames(0));
        assertEquals(20, k.forGames(10));
        assertEquals(10, k.forGames(30));
    }

    // --- teams -----------------------------------------------------------------

    @Test
    void aTeamIsRatedByItsAverage() {
        assertEquals(1000, TeamRating.average(List.of(1200, 800)));
        assertEquals(1000, TeamRating.average(List.of(1000)));
    }

    // --- floor -----------------------------------------------------------------

    @Test
    void ratingNeverDropsBelowTheFloor() {
        EloCalculator floored = new EloCalculator(KFactor.standard(), 100);

        // 0 games -> K = 40 ; equal opponents ; loss -> -20 -> 85, clamped to the 100 floor
        RatingChange change = floored.compute(new RatingSnapshot(105, 0), 105, 105, Outcome.LOSS);

        assertEquals(100, change.after());
    }
}

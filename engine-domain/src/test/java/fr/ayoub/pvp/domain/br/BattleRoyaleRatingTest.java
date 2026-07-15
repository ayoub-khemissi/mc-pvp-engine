package fr.ayoub.pvp.domain.br;

import fr.ayoub.pvp.domain.rating.KFactor;
import fr.ayoub.pvp.domain.rating.RatingChange;
import fr.ayoub.pvp.domain.rating.RatingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rating a battle royale, where there is no "other team" — just a finishing order.
 *
 * <p>The trick is to read a placement as a round-robin: you <b>beat</b> everyone who finished below
 * you and <b>lost</b> to everyone above, and each of those is an ordinary Elo comparison against that
 * player's rating. Sum the pairwise deltas, divide by the number of opponents so a 24-player match
 * moves a rating about as much as a single duel would, and add a flat bonus per kill. It reuses the
 * engine's existing Elo maths wholesale — a battle royale is just a lot of 1v1s resolved at once.
 *
 * <p>Placement dominates (that is what a battle royale is about); the kill bonus only leans the
 * result toward aggression, so a player who died early but took three others with them loses less
 * than one who hid and died early with none.
 */
class BattleRoyaleRatingTest {

    private static final KFactor K = KFactor.constant(32);

    private static BattleRoyaleRating.Standing at(int rating, int placement, int kills) {
        return new BattleRoyaleRating.Standing(new RatingSnapshot(rating, 100), placement, kills);
    }

    /** The two ends of an even field: winner gains, wooden spoon loses. */
    @Test
    void theWinnerGainsAndTheLastPlaceLoses() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                at(1000, 1, 0),
                at(1000, 2, 0),
                at(1000, 3, 0),
                at(1000, 4, 0)), K, 0);

        assertTrue(changes.get(0).delta() > 0, "winner");
        assertTrue(changes.get(3).delta() < 0, "last");
    }

    /** Elo without a kill bonus is (near) zero-sum: what the field gains, it also loses. */
    @Test
    void theEloPartIsZeroSum() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                at(1200, 1, 0),
                at(1000, 2, 0),
                at(1400, 3, 0),
                at(900, 4, 0),
                at(1100, 5, 0)), K, 0);

        int sum = changes.stream().mapToInt(RatingChange::delta).sum();
        assertTrue(Math.abs(sum) <= 2, "rounding aside, the table nets to zero: " + sum);
    }

    /** A whole match moves a rating about as much as one game — not (N-1) games. */
    @Test
    void oneMatchIsWorthRoughlyOneGame() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                at(1000, 1, 0),
                at(1000, 2, 0),
                at(1000, 3, 0),
                at(1000, 4, 0),
                at(1000, 5, 0),
                at(1000, 6, 0)), K, 0);

        // Winning the whole field is bounded by K (32), not K*(N-1).
        assertTrue(changes.get(0).delta() <= 32 && changes.get(0).delta() > 10,
                "winner moved " + changes.get(0).delta());
    }

    /** Beating higher-rated players is worth more; the favourite winning gains little. */
    @Test
    void anUpsetIsWorthMore() {
        List<RatingChange> underdogWins = BattleRoyaleRating.compute(List.of(
                at(800, 1, 0),
                at(1600, 2, 0)), K, 0);

        List<RatingChange> favouriteWins = BattleRoyaleRating.compute(List.of(
                at(1600, 1, 0),
                at(800, 2, 0)), K, 0);

        assertTrue(underdogWins.get(0).delta() > favouriteWins.get(0).delta());
    }

    @Test
    void killsAddAFlatBonusOnTop() {
        List<RatingChange> without = BattleRoyaleRating.compute(List.of(
                at(1000, 3, 0), at(1000, 1, 0), at(1000, 2, 0)), K, 5);
        List<RatingChange> with = BattleRoyaleRating.compute(List.of(
                at(1000, 3, 4), at(1000, 1, 0), at(1000, 2, 0)), K, 5);

        assertEquals(without.get(0).delta() + 4 * 5, with.get(0).delta(),
                "four kills at +5 each");
    }

    /** The point of the bonus: dying early with kills beats dying early with none. */
    @Test
    void aggressionSoftensABadPlacement() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                at(1000, 1, 0),
                at(1000, 5, 3),   // died 5th but took three
                at(1000, 4, 0),   // died 4th, hid
                at(1000, 3, 0),
                at(1000, 2, 0)), K, 6);

        // The 5th-place fragger comes out ahead of the quiet 4th-place finisher.
        assertTrue(changes.get(1).delta() > changes.get(2).delta());
    }

    @Test
    void ratingNeverGoesNegative() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                new BattleRoyaleRating.Standing(new RatingSnapshot(5, 100), 2, 0),
                new BattleRoyaleRating.Standing(new RatingSnapshot(1600, 100), 1, 0)),
                KFactor.constant(40), 0);

        assertTrue(changes.get(0).after() >= 0);
    }

    /** Two players who died at the same instant are a draw between themselves. */
    @Test
    void aTiedPlacementIsADrawBetweenThem() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(
                at(1000, 1, 0),
                at(1000, 1, 0)), K, 0);

        assertEquals(0, changes.get(0).delta());
        assertEquals(0, changes.get(1).delta());
    }

    @Test
    void aSoloSurvivorJustGetsTheirKillBonus() {
        List<RatingChange> changes = BattleRoyaleRating.compute(List.of(at(1000, 1, 2)), K, 5);

        assertEquals(10, changes.get(0).delta(), "no opponents, so only the two kills count");
    }

    @Test
    void refusesAnEmptyField() {
        assertThrows(IllegalArgumentException.class,
                () -> BattleRoyaleRating.compute(List.of(), K, 0));
    }

    @Test
    void refusesANegativeKillBonus() {
        assertThrows(IllegalArgumentException.class,
                () -> BattleRoyaleRating.compute(List.of(at(1000, 1, 0)), K, -1));
    }
}

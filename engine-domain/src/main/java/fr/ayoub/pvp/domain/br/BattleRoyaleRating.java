package fr.ayoub.pvp.domain.br;

import fr.ayoub.pvp.domain.rating.EloCalculator;
import fr.ayoub.pvp.domain.rating.KFactor;
import fr.ayoub.pvp.domain.rating.RatingChange;
import fr.ayoub.pvp.domain.rating.RatingSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Rating a battle royale from a finishing order.
 *
 * <p>There is no other team to compare against — just twenty-four people and the order they died in.
 * The insight is that a placement <b>is</b> a round-robin result: you beat everyone who finished
 * below you and lost to everyone above, and each of those is an ordinary Elo comparison against that
 * player's rating. So the match is scored as every pairwise duel at once, reusing the engine's Elo
 * maths untouched.
 *
 * <p>The pairwise deltas are divided by the number of opponents, so surviving a 24-player lobby moves
 * a rating about as much as winning a single duel would — not twenty-three times as much. On top of
 * that, a flat bonus per kill: placement is what a battle royale is about and it dominates, but the
 * bonus leans the reward toward aggression, so dying fifth with three kills beats dying fourth having
 * hidden the whole game.
 */
public final class BattleRoyaleRating {

    private BattleRoyaleRating() {
    }

    /**
     * One player's result. {@code placement} is 1 for the winner; players eliminated at the same
     * instant may share a placement and count as a draw between themselves.
     */
    public record Standing(RatingSnapshot rating, int placement, int kills) {

        public Standing {
            if (placement < 1) {
                throw new IllegalArgumentException("placement starts at 1, got " + placement);
            }
            if (kills < 0) {
                throw new IllegalArgumentException("kills cannot be negative, got " + kills);
            }
        }
    }

    /**
     * @param field      every player and where they finished, in any order
     * @param kFactor    how hard the match moves each rating (by the player's game count)
     * @param killBonus  flat rating added per kill, on top of the placement Elo
     * @return the rating change for each player, in the <b>same order</b> as {@code field}
     */
    public static List<RatingChange> compute(List<Standing> field, KFactor kFactor, int killBonus) {
        if (field.isEmpty()) {
            throw new IllegalArgumentException("nobody played this match");
        }
        if (killBonus < 0) {
            throw new IllegalArgumentException("a kill cannot cost rating: " + killBonus);
        }

        int opponents = field.size() - 1;
        List<RatingChange> changes = new ArrayList<>(field.size());

        for (Standing me : field) {
            double scoreLessExpected = 0;

            for (Standing other : field) {
                if (other == me) {
                    continue;
                }
                double expected = EloCalculator.expected(me.rating().rating(), other.rating().rating());
                double score = score(me.placement(), other.placement());
                scoreLessExpected += score - expected;
            }

            int k = kFactor.forGames(me.rating().games());
            double placementDelta = opponents == 0 ? 0 : k * scoreLessExpected / opponents;

            int after = (int) Math.round(me.rating().rating() + placementDelta) + me.kills() * killBonus;
            changes.add(new RatingChange(me.rating().rating(), Math.max(EloCalculator.NO_FLOOR, after)));
        }
        return changes;
    }

    /** 1 for finishing above them, 0 below, 0.5 for going out together. */
    private static double score(int myPlacement, int theirPlacement) {
        if (myPlacement < theirPlacement) {
            return 1.0;
        }
        if (myPlacement > theirPlacement) {
            return 0.0;
        }
        return 0.5;
    }
}

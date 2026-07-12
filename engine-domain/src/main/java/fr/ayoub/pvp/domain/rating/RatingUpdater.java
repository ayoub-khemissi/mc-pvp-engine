package fr.ayoub.pvp.domain.rating;

/**
 * Applies a match result to a player's record.
 *
 * Small, but exactly the kind of code that quietly goes wrong: a streak that
 * decrements instead of resetting, a peak that follows the rating down, a draw
 * counted as a loss. All of that is pinned by tests.
 */
public final class RatingUpdater {

    private RatingUpdater() {
    }

    public static PlayerRating apply(PlayerRating current, int newRating, Outcome outcome) {
        int wins = current.wins() + (outcome == Outcome.WIN ? 1 : 0);
        int losses = current.losses() + (outcome == Outcome.LOSS ? 1 : 0);

        return new PlayerRating(
                newRating,
                Math.max(current.peak(), newRating),
                current.games() + 1,
                wins,
                losses,
                nextStreak(current.streak(), outcome));
    }

    /** A win after losses starts again at +1 — a streak is broken, not merely nudged. */
    private static int nextStreak(int streak, Outcome outcome) {
        return switch (outcome) {
            case WIN -> streak > 0 ? streak + 1 : 1;
            case LOSS -> streak < 0 ? streak - 1 : -1;
            case DRAW -> 0;
        };
    }
}

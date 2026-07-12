package fr.ayoub.pvp.domain.rating;

import java.util.Objects;

/**
 * Standard Elo.
 *
 * <pre>
 *   expected = 1 / (1 + 10 ^ ((opponent - me) / 400))
 *   new      = me + K * (score - expected)
 * </pre>
 *
 * For team matches, both sides are compared by their <b>average</b> rating
 * (see {@link TeamRating}), and the resulting delta is applied to each member.
 *
 * Pure maths — no Bukkit, no database. Fully unit-tested.
 */
public final class EloCalculator {

    /** Ratings are not allowed to go below this by default. */
    public static final int NO_FLOOR = 0;

    private final KFactor kFactor;
    private final int floor;

    public EloCalculator(KFactor kFactor) {
        this(kFactor, NO_FLOOR);
    }

    public EloCalculator(KFactor kFactor, int floor) {
        this.kFactor = Objects.requireNonNull(kFactor, "kFactor");
        this.floor = floor;
    }

    /** Probability that a player rated {@code rating} beats one rated {@code opponentRating}. */
    public static double expected(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
    }

    /**
     * The new rating of one player.
     *
     * @param player               the player's rating and experience
     * @param ownTeamAverage       average rating of the player's team
     * @param opponentTeamAverage  average rating of the other team
     * @param outcome              result from the player's team's point of view
     */
    public RatingChange compute(RatingSnapshot player,
                                int ownTeamAverage,
                                int opponentTeamAverage,
                                Outcome outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(outcome, "outcome");

        double expected = expected(ownTeamAverage, opponentTeamAverage);
        int k = kFactor.forGames(player.games());

        int after = (int) Math.round(player.rating() + k * (outcome.score() - expected));
        return new RatingChange(player.rating(), Math.max(floor, after));
    }
}

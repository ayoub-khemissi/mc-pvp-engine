package fr.ayoub.pvp.domain.rating;

/**
 * A player's full record for one (mode, format).
 *
 * @param streak positive = wins in a row, negative = losses in a row, 0 = neither
 */
public record PlayerRating(int rating, int peak, int games, int wins, int losses, int streak) {

    public static PlayerRating initial(int startingRating) {
        return new PlayerRating(startingRating, startingRating, 0, 0, 0, 0);
    }

    /** What the Elo maths needs. */
    public RatingSnapshot toSnapshot() {
        return new RatingSnapshot(rating, games);
    }

    public double winRate() {
        int decided = wins + losses;
        return decided == 0 ? 0 : (double) wins / decided;
    }
}

package fr.ayoub.pvp.storage;

import fr.ayoub.pvp.domain.rating.PlayerRating;
import fr.ayoub.pvp.domain.rating.RatingSnapshot;

/** A row of the {@code ratings} table: one player, one mode, one format. */
public record RatingRow(int rating, int peakRating, int games, int wins, int losses, int streak) {

    /** A player who has never played this mode/format. */
    public static RatingRow initial(int startingRating) {
        return new RatingRow(startingRating, startingRating, 0, 0, 0, 0);
    }

    public static RatingRow of(PlayerRating rating) {
        return new RatingRow(
                rating.rating(),
                rating.peak(),
                rating.games(),
                rating.wins(),
                rating.losses(),
                rating.streak());
    }

    /** The domain view — where the Elo rules live. */
    public PlayerRating toDomain() {
        return new PlayerRating(rating, peakRating, games, wins, losses, streak);
    }

    public RatingSnapshot toSnapshot() {
        return new RatingSnapshot(rating, games);
    }
}

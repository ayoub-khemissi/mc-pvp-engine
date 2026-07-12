package fr.ayoub.pvp.domain.rating;

/** A player's rating state before a match. */
public record RatingSnapshot(int rating, int games) {

    public RatingSnapshot {
        if (games < 0) {
            throw new IllegalArgumentException("games must be >= 0, got " + games);
        }
    }
}

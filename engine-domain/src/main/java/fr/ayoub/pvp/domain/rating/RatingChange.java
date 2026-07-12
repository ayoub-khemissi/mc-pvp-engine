package fr.ayoub.pvp.domain.rating;

/** The rating a player had before a match, and the one they have after. */
public record RatingChange(int before, int after) {

    /** Positive when the player gained rating. Shown to the player as "+18". */
    public int delta() {
        return after - before;
    }
}

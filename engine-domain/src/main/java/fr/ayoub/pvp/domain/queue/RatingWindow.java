package fr.ayoub.pvp.domain.queue;

/**
 * How picky the matchmaker is.
 *
 * It starts strict (only opponents within ±{@code initial}) and relaxes the longer
 * someone waits, so a very strong or very weak player is never stuck in the queue
 * forever — but it never relaxes past {@code max}, so a 500-rated player is never
 * thrown at a 2000-rated one.
 */
public record RatingWindow(int initial, int growthPerFiveSeconds, int max) {

    public RatingWindow {
        if (initial < 0 || growthPerFiveSeconds < 0 || max < initial) {
            throw new IllegalArgumentException("invalid rating window");
        }
    }

    /** The allowed rating spread for someone who has waited this long. */
    public int widthAt(long waitedMillis) {
        long steps = Math.max(0, waitedMillis) / 5_000L;
        long width = initial + steps * growthPerFiveSeconds;
        return (int) Math.min(width, max);
    }
}

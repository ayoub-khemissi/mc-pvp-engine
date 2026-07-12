package fr.ayoub.pvp.api;

/**
 * @param rounds           best-of; must be odd (1, 3, 5…) so a series always has a winner
 * @param countdownSeconds 3 · 2 · 1 · FIGHT — before every round
 * @param timeLimitSeconds 0 = no limit
 */
public record MatchRules(int rounds, int countdownSeconds, int timeLimitSeconds) {

    public MatchRules {
        if (rounds < 1 || rounds % 2 == 0) {
            throw new IllegalArgumentException("best-of must be odd (1, 3, 5…), got " + rounds);
        }
        if (countdownSeconds < 0) {
            throw new IllegalArgumentException("countdown cannot be negative");
        }
    }

    /** Best of 3 — one lucky hit should not decide a ranked match. */
    public static MatchRules standard() {
        return new MatchRules(3, 5, 300);
    }

    public boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }
}

package fr.ayoub.pvp.api;

/**
 * @param rounds           best-of; 1 = a single fight
 * @param countdownSeconds 3 · 2 · 1 · FIGHT
 * @param timeLimitSeconds 0 = no limit
 */
public record MatchRules(int rounds, int countdownSeconds, int timeLimitSeconds) {

    public MatchRules {
        if (rounds < 1) {
            throw new IllegalArgumentException("a match needs at least 1 round");
        }
        if (countdownSeconds < 0) {
            throw new IllegalArgumentException("countdown cannot be negative");
        }
    }

    public static MatchRules standard() {
        return new MatchRules(1, 5, 300);
    }

    public boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }
}

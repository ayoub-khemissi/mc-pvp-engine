package fr.ayoub.pvp.api;

/**
 * @param rounds           best-of; must be odd (1, 3, 5…) so a series always has a winner
 * @param countdownSeconds 3 · 2 · 1 · FIGHT — before every round
 * @param timeLimitSeconds 0 = no limit
 * @param building         may players break and place blocks?
 *                         <p>
 *                         This is not just a permission, it decides the <b>game mode</b>.
 *                         {@code false} → ADVENTURE: the client itself refuses to break a
 *                         block, so nothing is even animated. {@code true} → SURVIVAL, and
 *                         the engine stops cancelling breaks and placements — for a mode
 *                         that is about building (a bridge, a wall).
 */
public record MatchRules(int rounds, int countdownSeconds, int timeLimitSeconds, boolean building) {

    public MatchRules {
        if (rounds < 1 || rounds % 2 == 0) {
            throw new IllegalArgumentException("best-of must be odd (1, 3, 5…), got " + rounds);
        }
        if (countdownSeconds < 0) {
            throw new IllegalArgumentException("countdown cannot be negative");
        }
    }

    /** Best of 3, no building — one lucky hit should not decide a ranked match. */
    public static MatchRules standard() {
        return new MatchRules(3, 5, 300, false);
    }

    public boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }
}

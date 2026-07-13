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
public record MatchRules(int rounds, int countdownSeconds, int timeLimitSeconds,
                         boolean building, int respawnSeconds) {

    public MatchRules {
        if (rounds < 1 || rounds % 2 == 0) {
            throw new IllegalArgumentException("best-of must be odd (1, 3, 5…), got " + rounds);
        }
        if (countdownSeconds < 0) {
            throw new IllegalArgumentException("countdown cannot be negative");
        }
        if (respawnSeconds < 0) {
            throw new IllegalArgumentException("respawn delay cannot be negative");
        }
    }

    /** Best of 3, no building, no respawn — one lucky hit should not decide a ranked match. */
    public static MatchRules standard() {
        return new MatchRules(3, 5, 300, false, 0);
    }

    /**
     * Do the dead come back?
     *
     * <b>0 means they do not</b>, and that changes the whole shape of a match: a duel ends
     * when a team is wiped out, so "last team standing" IS the win condition. Turn respawn on
     * and that condition disappears — everybody is always coming back — so the mode has to
     * bring a win condition of its own (a crystal, a payload, a clock). The engine cannot
     * guess one, and a match with neither would simply never end.
     */
    public boolean hasRespawn() {
        return respawnSeconds > 0;
    }

    public boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }
}

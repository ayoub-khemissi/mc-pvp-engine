package fr.ayoub.pvp.domain.queue;

/**
 * When a gathering-style queue starts a match, and with how many.
 *
 * <p>A duel's {@link Matchmaker} pairs two equal tickets and goes. A battle royale does not pair —
 * it <b>accumulates</b> solo players into one big match and starts on a threshold and a timer:
 * immediately once it is full, or once a minimum is present and the first player has waited long
 * enough that holding out for a full lobby would be the worse experience. Pure arithmetic, so the
 * engine's queue reads it without knowing what a battle royale is.
 *
 * @param minPlayers   fewest players a match may start with — below this it never starts, however
 *                     long anyone has waited
 * @param maxPlayers   the cap; a match takes at most this many and the rest wait for the next one
 * @param maxWaitSecs  once {@code minPlayers} are present, start after this many seconds rather than
 *                     hold everyone hostage to a full lobby
 */
public record GatherRule(int minPlayers, int maxPlayers, int maxWaitSecs) {

    public GatherRule {
        if (minPlayers < 2) {
            throw new IllegalArgumentException("a match needs at least 2 players, got " + minPlayers);
        }
        if (maxPlayers < minPlayers) {
            throw new IllegalArgumentException("the cap " + maxPlayers + " is below the floor " + minPlayers);
        }
        if (maxWaitSecs < 0) {
            throw new IllegalArgumentException("the wait cannot be negative, got " + maxWaitSecs);
        }
    }

    /**
     * @param queued            how many are waiting in this queue
     * @param oldestWaitSeconds how long the player who has waited longest has waited
     * @return whether to start a match now
     */
    public boolean shouldStart(int queued, int oldestWaitSeconds) {
        if (queued < minPlayers) {
            return false;
        }
        return queued >= maxPlayers || oldestWaitSeconds >= maxWaitSecs;
    }

    /** How many of the {@code queued} players this match takes: all of them, up to the cap. */
    public int take(int queued) {
        return Math.min(queued, maxPlayers);
    }
}

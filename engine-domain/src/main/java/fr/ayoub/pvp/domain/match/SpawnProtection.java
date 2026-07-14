package fr.ayoub.pvp.domain.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The few seconds a player cannot be killed after coming back from the dead.
 *
 * <p>Without it, a mode with respawn has a spawn camp in it. The strongest play is to stand on the
 * enemy's spawn and kill them as they materialise, again and again, and there is nothing they can
 * do: they are dead before the world has finished drawing itself around them. Every mode with
 * respawn needs this, which is why it is the engine's and not any one mode's.
 *
 * <p><b>It must not become a weapon.</b> A player who can hit while they cannot be hit does not
 * have a shield — they have five free seconds. Die on purpose, walk into the fight, swing with
 * impunity. So the protection ends the instant they attack. The blow still lands: they chose to
 * fight, and that choice is exactly what costs them the shield.
 *
 * <p>Time is passed in rather than read, so this is pure and can be tested in microseconds.
 */
public final class SpawnProtection {

    private final long durationMillis;
    private final Map<UUID, Long> until = new HashMap<>();

    /** @param durationMillis 0 turns it off entirely — a kit mode with no respawn wants none */
    public SpawnProtection(long durationMillis) {
        if (durationMillis < 0) {
            throw new IllegalArgumentException("a negative duration protects nobody, backwards");
        }
        this.durationMillis = durationMillis;
    }

    public void grant(UUID player, long nowMillis) {
        if (durationMillis > 0) {
            until.put(player, nowMillis + durationMillis);
        }
    }

    public boolean covers(UUID player, long nowMillis) {
        Long ends = until.get(player);
        return ends != null && nowMillis < ends;
    }

    /**
     * They swung at somebody. Take it away.
     *
     * @return true if they actually had it — so the caller can tell them once, and not on every
     *         blow of the fight they have just started
     */
    public boolean dropped(UUID player) {
        return until.remove(player) != null;
    }

    /** For the countdown on their screen. Rounded up: 0.2 seconds left is still "1". */
    public int secondsLeft(UUID player, long nowMillis) {
        Long ends = until.get(player);
        if (ends == null || nowMillis >= ends) {
            return 0;
        }
        return (int) Math.ceil((ends - nowMillis) / 1000.0);
    }

    public void clear() {
        until.clear();
    }
}

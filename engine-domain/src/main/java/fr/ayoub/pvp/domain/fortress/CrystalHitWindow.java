package fr.ayoub.pvp.domain.fortress;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The half-second an attacker must wait before their next swing counts on a crystal.
 *
 * <p><b>Why it exists.</b> Minecraft scales a swing by how far the attack cooldown has recharged
 * — 20% of the weapon's damage if it was mashed, 100% if the player waited — and it is tempting
 * to think that alone rewards patience. It does not. A diamond sword does 7 over a 0.625 s
 * recharge: 11.2 damage a second if you wait for it. Mash at ten clicks a second and each swing
 * lands at 0.2 + 0.16² × 0.8 = 22%, which is 1.54 damage ten times a second — <b>15.4</b>.
 * Spamming wins, and the mechanic that was supposed to stop it is the one paying for it.
 *
 * <p>Vanilla is not broken this way because a mob is a {@code LivingEntity}, and a LivingEntity
 * is invulnerable for half a second after being hurt. The two mechanics only work as a pair. An
 * End Crystal is <b>not</b> a LivingEntity — it has no such window, and never had one. This is
 * that missing half-second.
 *
 * <p><b>Why the window belongs to the attacker, not the crystal.</b> Vanilla's belongs to the
 * victim, which is right for a mob and wrong for an objective: three players hitting one crystal
 * would swallow each other's blows, and a three-man push would break it no faster than one man
 * alone. Here every attacker carries their own. Mashing is capped per player; a team is still
 * worth its size.
 */
public final class CrystalHitWindow {

    /** The last blow an attacker landed: when, and how big. */
    private record Landed(long tick, double amount) {
    }

    private final long windowTicks;
    private final Map<UUID, Landed> last = new HashMap<>();

    /**
     * @param windowTicks how long an attacker's blow keeps the crystal to itself.
     *                    10 (half a second) is vanilla's. 0 disables the whole thing.
     */
    public CrystalHitWindow(long windowTicks) {
        if (windowTicks < 0) {
            throw new IllegalArgumentException("the window cannot be negative, was " + windowTicks);
        }
        this.windowTicks = windowTicks;
    }

    /**
     * How much of this blow actually lands.
     *
     * <p>Outside the window: all of it, and the window opens again. Inside it: only what it has
     * <b>over</b> the blow that opened the window — vanilla's rule, and it matters, because
     * without it a player could tap the crystal for 1 and use that as a shield against the 7 they
     * are about to land. Being swallowed does not push the window back, or a spammer could hold
     * it open forever by never stopping.
     */
    public double admit(UUID attacker, double amount, long tick) {
        if (amount <= 0) {
            return 0;
        }

        Landed previous = last.get(attacker);
        boolean open = previous == null || tick - previous.tick() >= windowTicks;

        if (open) {
            last.put(attacker, new Landed(tick, amount));
            return amount;
        }

        double excess = amount - previous.amount();
        if (excess <= 0) {
            return 0;
        }

        // The bar to beat is raised, but the clock is NOT restarted.
        last.put(attacker, new Landed(previous.tick(), amount));
        return excess;
    }

    /** They left, or the match is over. */
    public void forget(UUID attacker) {
        last.remove(attacker);
    }

    public void clear() {
        last.clear();
    }
}

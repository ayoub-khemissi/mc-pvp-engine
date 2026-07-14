package fr.ayoub.pvp.domain.fortress;

/**
 * The crystal's health pool.
 *
 * Minecraft's own End Crystal has <b>one</b> hit point and explodes when it loses it. A
 * fortress whose crystal dies to the first arrow anybody looses at it is not a fortress, it is
 * a formality — so the mode keeps the health that actually counts, here.
 *
 * <p><b>It is a double, and that is deliberate.</b> It used to be an int, and every blow was
 * rounded and then floored to a minimum of one. Minecraft already scales an attack by how far
 * the cooldown has recharged: a spammed swing lands at 20% of the weapon's damage, a patient
 * one at 100%. Round 20% of a weak weapon and you get zero; floor it to one and a player who
 * mashes the button four times a second out-damages the player who waits. The floor did not
 * merely lose precision — it <b>inverted the rule it was meant to enforce</b>.
 */
public final class CrystalHealth {

    private final double max;
    private double current;

    public CrystalHealth(double max) {
        if (max <= 0) {
            throw new IllegalArgumentException("a crystal needs health, was given " + max);
        }
        this.max = max;
        this.current = max;
    }

    /**
     * @param amount what the blow was worth, fractions and all
     * @return true if that was the blow that broke it
     */
    public boolean damage(double amount) {
        if (amount > 0) {
            current = Math.max(0, current - amount);
        }
        return isDead();
    }

    public double current() {
        return current;
    }

    public double max() {
        return max;
    }

    public boolean isDead() {
        return current <= 0;
    }

    /**
     * What the boss bar says.
     *
     * Rounded <b>up</b> while it is still standing: a crystal on its last 0.2 points is not
     * dead, and a bar reading "0" over a crystal that is still there is a lie the defenders
     * would act on. It reads zero when, and only when, it is actually gone.
     */
    public int display() {
        return isDead() ? 0 : (int) Math.ceil(current);
    }

    /** 0.0 … 1.0, for the bar. */
    public float fraction() {
        return (float) (current / max);
    }
}

package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystal's health pool.
 *
 * It is a <b>double</b>, and that is the whole point of this class. It used to be an int, and
 * every blow was rounded and then floored to a minimum of one:
 *
 * <pre>int damage = (int) Math.max(1, Math.round(event.getDamage()));</pre>
 *
 * <p>That one line broke the mode's central mechanic. Minecraft already scales an attack by
 * how far the cooldown has recharged — a spammed swing lands at 20% of the weapon's damage, a
 * patient one at 100%. Round 20% of a weak weapon and you get zero; floor it to one and a
 * player who mashes the button four times a second out-damages the player who waits. The floor
 * did not merely lose precision, it <b>inverted the rule it was supposed to enforce</b>.
 *
 * <p>So: no rounding, no floor. A 0.28-damage blow takes 0.28, and four of them take 1.12.
 */
class CrystalHealthTest {

    @Test
    void startsFull() {
        CrystalHealth health = new CrystalHealth(250);

        assertEquals(250, health.current());
        assertEquals(250, health.max());
        assertEquals(1f, health.fraction());
        assertFalse(health.isDead());
    }

    /** THE regression test. Small blows used to be rounded up to 1, or away to nothing. */
    @Test
    void takesFractionalDamage() {
        CrystalHealth health = new CrystalHealth(10);

        assertFalse(health.damage(0.28));
        assertEquals(9.72, health.current(), 1e-9);

        health.damage(0.28);
        health.damage(0.28);
        health.damage(0.28);
        assertEquals(10 - 4 * 0.28, health.current(), 1e-9);
    }

    /**
     * The mechanic the floor destroyed, stated as a test.
     *
     * A netherite sword does 8. Spammed, Minecraft hands us 8 × 0.2 = 1.6; fully charged, 8.
     * Five spammed swings must not be worth what one patient swing is worth — they are worth
     * exactly the 8 they add up to, and they take five times as long to land.
     */
    @Test
    void punishesSpamming() {
        CrystalHealth spammed = new CrystalHealth(100);
        CrystalHealth patient = new CrystalHealth(100);

        spammed.damage(1.6);
        patient.damage(8.0);

        assertTrue(spammed.current() > patient.current(),
                "a spammed blow must hurt less than a charged one");
    }

    @Test
    void neverFallsBelowZero() {
        CrystalHealth health = new CrystalHealth(5);

        assertTrue(health.damage(500));
        assertEquals(0, health.current());
        assertEquals(0f, health.fraction());
        assertTrue(health.isDead());
    }

    @Test
    void saysWhenTheBlowWasTheLastOne() {
        CrystalHealth health = new CrystalHealth(10);

        assertFalse(health.damage(9.5));
        assertTrue(health.damage(0.5));
        assertTrue(health.isDead());
    }

    @Test
    void ignoresABlowThatDoesNothing() {
        CrystalHealth health = new CrystalHealth(10);

        health.damage(0);
        health.damage(-5);

        assertEquals(10, health.current());
    }

    /**
     * What the boss bar says. A crystal on its last 0.2 points is <b>not dead</b>, and a bar
     * reading "0" over a crystal that is still standing is a lie the defenders would act on.
     */
    @Test
    void roundsTheDisplayUpWhileItIsStillAlive() {
        CrystalHealth health = new CrystalHealth(250);

        health.damage(249.8);

        assertFalse(health.isDead());
        assertEquals(1, health.display());
    }

    @Test
    void showsZeroOnlyWhenItIsActuallyDead() {
        CrystalHealth health = new CrystalHealth(250);

        health.damage(250);

        assertEquals(0, health.display());
    }

    @Test
    void refusesAnImpossiblePool() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalHealth(0));
        assertThrows(IllegalArgumentException.class, () -> new CrystalHealth(-1));
    }
}

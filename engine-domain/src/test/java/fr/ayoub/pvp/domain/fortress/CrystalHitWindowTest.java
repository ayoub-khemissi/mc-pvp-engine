package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The half-second an attacker has to wait before their next swing counts.
 *
 * <p><b>Why this exists at all.</b> Minecraft scales an attack by how far the cooldown has
 * recharged — 20% of the weapon's damage for a spammed swing, 100% for a patient one — and it
 * would be reasonable to think that alone punishes button-mashing. It does not. A diamond sword
 * does 7 over a 0.625 s recharge: 11.2 damage a second if you wait. Mash at ten clicks a second
 * and each swing lands at 0.2 + 0.16² × 0.8 = 22% — 1.54 damage, ten times a second, <b>15.4 a
 * second</b>. Spamming wins.
 *
 * <p>Vanilla gets away with it because a mob is a {@code LivingEntity}, and a LivingEntity is
 * invulnerable for half a second after it is hurt. An End Crystal is <b>not</b> a LivingEntity:
 * it has no such window, and never did. That missing half-second is the entire bug.
 *
 * <p><b>Why it is per attacker.</b> Vanilla's window belongs to the victim, which is right for a
 * mob and wrong for an objective: three players hitting one crystal would eat each other's blows
 * and a three-man push would break it no faster than one man alone. So the window belongs to the
 * <b>attacker</b>. Spam is capped individually; a team is still worth its size.
 */
class CrystalHitWindowTest {

    private static final long HALF_SECOND = 10;   // ticks, as in vanilla

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void theFirstBlowLandsInFull() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        assertEquals(7.0, window.admit(alice, 7.0, 100));
    }

    @Test
    void swallowsASecondBlowInsideTheWindow() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        window.admit(alice, 7.0, 100);

        assertEquals(0.0, window.admit(alice, 1.5, 102));
        assertEquals(0.0, window.admit(alice, 7.0, 104));
    }

    @Test
    void landsAgainOnceTheWindowHasPassed() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        window.admit(alice, 7.0, 100);

        assertEquals(7.0, window.admit(alice, 7.0, 110));
    }

    /**
     * Vanilla's rule, and it matters: a small blow must not be usable as a <b>shield</b> against
     * a big one. Tap the crystal for 1, and the 7 that lands a tick later still costs it 6.
     */
    @Test
    void aBiggerBlowInsideTheWindowStillLandsItsExcess() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        window.admit(alice, 1.0, 100);

        assertEquals(6.0, window.admit(alice, 7.0, 102));
        assertEquals(0.0, window.admit(alice, 7.0, 104), "the 7 is now the bar to beat");
    }

    /** Being swallowed must not push the window back, or a spammer could hold it open forever. */
    @Test
    void aSwallowedBlowDoesNotExtendTheWindow() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        window.admit(alice, 7.0, 100);
        for (long tick = 101; tick < 110; tick++) {
            window.admit(alice, 7.0, tick);       // mashing, all swallowed
        }

        assertEquals(7.0, window.admit(alice, 7.0, 110), "the window still opened on time");
    }

    /**
     * THE reason the window is not vanilla's. Three players pushing a crystal together must be
     * worth three players — if they shared a window they would be worth one.
     */
    @Test
    void oneAttackerNeverEatsAnothersBlow() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        assertEquals(7.0, window.admit(alice, 7.0, 100));
        assertEquals(7.0, window.admit(bob, 7.0, 100), "Bob is not on Alice's cooldown");
    }

    @Test
    void aWindowOfZeroLetsEverythingThrough() {
        CrystalHitWindow window = new CrystalHitWindow(0);

        assertEquals(7.0, window.admit(alice, 7.0, 100));
        assertEquals(7.0, window.admit(alice, 7.0, 100));
    }

    @Test
    void forgetsAnAttackerWhoIsGone() {
        CrystalHitWindow window = new CrystalHitWindow(HALF_SECOND);

        window.admit(alice, 7.0, 100);
        window.forget(alice);

        assertEquals(7.0, window.admit(alice, 7.0, 101));
    }

    @Test
    void refusesANegativeWindow() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalHitWindow(-1));
    }
}

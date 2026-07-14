package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The few seconds a player cannot be killed after coming back.
 *
 * <p>Without it, a mode with respawn has a spawn camp: the fastest way to win is to stand on the
 * enemy's spawn pad and kill them as they materialise, over and over, and there is nothing they can
 * do about it — they are dead before the world has finished loading around them.
 *
 * <p><b>But it must not become a weapon.</b> A player who can hit while they cannot be hit does not
 * have a shield, they have five free seconds: die on purpose, walk into the fight, swing with
 * impunity. So the protection ends the instant they attack. The blow still lands — they chose to
 * fight, and that choice is what costs them the shield.
 */
class SpawnProtectionTest {

    private static final long FIVE_SECONDS = 5_000;

    private final UUID player = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void coversAPlayerWhoJustCameBack() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);

        protection.grant(player, 1_000);

        assertTrue(protection.covers(player, 1_000));
        assertTrue(protection.covers(player, 4_999));
    }

    @Test
    void runsOut() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);

        protection.grant(player, 1_000);

        assertFalse(protection.covers(player, 6_000));
        assertFalse(protection.covers(player, 60_000));
    }

    @Test
    void coversNobodyItWasNotGivenTo() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);

        protection.grant(player, 1_000);

        assertFalse(protection.covers(other, 1_000));
    }

    /** THE rule. A shield you can swing from is not a shield, it is five free seconds. */
    @Test
    void endsTheMomentTheyAttack() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);
        protection.grant(player, 1_000);

        protection.dropped(player);

        assertFalse(protection.covers(player, 1_500), "they swung, so they are fair game");
    }

    @Test
    void tellsYouWhetherItActuallyDroppedAnything() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);
        protection.grant(player, 1_000);

        assertTrue(protection.dropped(player), "they had it, and now they do not");
        assertFalse(protection.dropped(player), "they had already lost it — say nothing twice");
        assertFalse(protection.dropped(other), "they never had it");
    }

    /**
     * Rounded <b>up</b>, which is what a countdown has to do: with 3.5 seconds left the screen
     * says 4 and then says 3, and never shows a 0 over a player who still cannot be hit.
     */
    @Test
    void howLongIsLeft() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);
        protection.grant(player, 1_000);   // it runs out at 6_000

        assertEquals(5, protection.secondsLeft(player, 1_000));
        assertEquals(4, protection.secondsLeft(player, 2_500));   // 3.5s left
        assertEquals(2, protection.secondsLeft(player, 4_900));   // 1.1s left
        assertEquals(1, protection.secondsLeft(player, 5_900));   // 0.1s left, and still covered
        assertEquals(0, protection.secondsLeft(player, 9_000));
    }

    /** A mode that did not ask for it gets none, and does not pay for a lookup to find that out. */
    @Test
    void protectsNobodyWhenItIsTurnedOff() {
        SpawnProtection protection = new SpawnProtection(0);

        protection.grant(player, 1_000);

        assertFalse(protection.covers(player, 1_000));
    }

    @Test
    void forgetsEverybodyWhenTheMatchIsOver() {
        SpawnProtection protection = new SpawnProtection(FIVE_SECONDS);
        protection.grant(player, 1_000);

        protection.clear();

        assertFalse(protection.covers(player, 1_500));
    }

    @Test
    void refusesANegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SpawnProtection(-1));
    }
}

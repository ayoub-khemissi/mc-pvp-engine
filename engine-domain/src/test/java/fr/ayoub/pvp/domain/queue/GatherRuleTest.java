package fr.ayoub.pvp.domain.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When to start a gathering-style match — the battle royale queue, as opposed to the duel's
 * pairwise one.
 *
 * <p>A duel pairs two equal tickets and goes. A battle royale instead <b>accumulates</b> solo
 * players and starts one big match on a threshold and a timer: as soon as it is full, or once a
 * minimum is present and the wait has run long enough that making them wait for a full lobby would
 * be worse than starting short. Pure arithmetic over "how many are queued" and "how long has the
 * first one waited".
 */
class GatherRuleTest {

    /** Start at 8, cap at 24, don't make people wait past 60s. */
    private static GatherRule standard() {
        return new GatherRule(8, 24, 60);
    }

    @Test
    void startsAsSoonAsItIsFull() {
        assertTrue(standard().shouldStart(24, 0));
        assertTrue(standard().shouldStart(30, 0), "more than the cap have queued — still start");
    }

    @Test
    void waitsForTheMinimum() {
        assertFalse(standard().shouldStart(7, 999), "seven is below the floor, however long they wait");
    }

    @Test
    void startsShortOnceTheMinimumHasWaitedLongEnough() {
        assertFalse(standard().shouldStart(8, 59), "at the floor, but the timer has not run out");
        assertTrue(standard().shouldStart(8, 60), "the floor, and the wait is up — go with what we have");
    }

    @Test
    void doesNotStartBelowTheMinimumEvenAtTheTimer() {
        assertFalse(standard().shouldStart(5, 120));
    }

    /** However many are queued, a match takes at most the cap. The rest wait for the next one. */
    @Test
    void takesAtMostTheCap() {
        assertEquals(24, standard().take(24));
        assertEquals(24, standard().take(50));
        assertEquals(10, standard().take(10));
    }

    @Test
    void refusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new GatherRule(1, 24, 60));   // min < 2
        assertThrows(IllegalArgumentException.class, () -> new GatherRule(8, 4, 60));    // cap < min
        assertThrows(IllegalArgumentException.class, () -> new GatherRule(8, 24, -1));   // negative wait
    }
}

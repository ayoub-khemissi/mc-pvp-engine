package fr.ayoub.pvp.domain.br;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shrinking storm, as a pure timeline.
 *
 * <p>A battle royale's zone is a sequence of phases: the ring <b>holds</b> for a while (players see
 * where they have to be and run for it), then <b>closes</b> to a smaller ring over some seconds, and
 * the damage a caught player takes goes up each phase — gentle early, lethal at the end, exactly
 * like Fortnite. Since the ring always closes toward the centre, a phase is fully described by a
 * radius; no moving centre to track.
 *
 * <p>This is all of that with no Bukkit and no clock: hand it the elapsed seconds and it tells you
 * the radius right now, whether it is holding or closing, what a caught player is taking, and how
 * long until the next thing happens — which is everything the world border and the HUD need.
 */
class StormScheduleTest {

    /** Full ring 200 → holds 30s, closes to 100 over 20s → holds 15s, closes to 40 over 15s. */
    private static StormSchedule twoPhases() {
        return new StormSchedule(200, List.of(
                new StormPhase(30, 20, 100, 0.5),
                new StormPhase(15, 15, 40, 2.0)));
    }

    @Test
    void holdsTheFullRingAtTheStart() {
        StormState state = twoPhases().at(0);

        assertEquals(StormState.Mode.HOLDING, state.mode());
        assertEquals(200, state.radius());
        assertEquals(0, state.phase());
        assertEquals(30, state.secondsUntilNext());   // until the first close begins
        assertFalse(state.finished());
    }

    @Test
    void holdsUntilTheCloseBegins() {
        StormState state = twoPhases().at(29);

        assertEquals(StormState.Mode.HOLDING, state.mode());
        assertEquals(200, state.radius());
        assertEquals(1, state.secondsUntilNext());
    }

    @Test
    void closesSmoothlyTowardsTheNextRadius() {
        // Close is 200 -> 100 over 20s, starting at t=30. Halfway (t=40) is radius 150.
        StormState state = twoPhases().at(40);

        assertEquals(StormState.Mode.CLOSING, state.mode());
        assertEquals(150, state.radius(), 1e-9);
        assertEquals(0, state.phase());
        assertEquals(10, state.secondsUntilNext());   // until the close finishes
    }

    @Test
    void reachesTheNextRadiusExactly() {
        StormState state = twoPhases().at(50);   // 30 hold + 20 close

        assertEquals(100, state.radius(), 1e-9);
    }

    @Test
    void holdsAgainOnTheNextRing() {
        StormState state = twoPhases().at(60);   // 10s into phase 1's 15s hold, at radius 100

        assertEquals(StormState.Mode.HOLDING, state.mode());
        assertEquals(1, state.phase());
        assertEquals(100, state.radius());
        assertEquals(5, state.secondsUntilNext());
    }

    @Test
    void closesTheFinalRing() {
        // phase 1 close is 100 -> 40 over 15s, starting at t = 30+20+15 = 65. Halfway t=72.5.
        StormState state = twoPhases().at(72);

        assertEquals(StormState.Mode.CLOSING, state.mode());
        assertEquals(1, state.phase());
        assertTrue(state.radius() < 100 && state.radius() > 40);
    }

    @Test
    void thePenaltyRisesEachPhase() {
        assertEquals(0.5, twoPhases().at(0).damagePerTick());    // phase 0
        assertEquals(2.0, twoPhases().at(60).damagePerTick());   // phase 1
    }

    @Test
    void endsAtTheFinalRingAndStaysThere() {
        StormState state = twoPhases().at(1000);

        assertTrue(state.finished());
        assertEquals(StormState.Mode.FINISHED, state.mode());
        assertEquals(40, state.radius());
        assertEquals(2.0, state.damagePerTick());   // the last ring keeps killing
    }

    @Test
    void knowsTheWholeThingIsWorthSitting() {
        assertEquals(30 + 20 + 15 + 15, twoPhases().totalSeconds());
    }

    /** A caught player: are they outside the ring right now? Centre-relative distance. */
    @Test
    void tellsWhoIsInTheStorm() {
        StormState state = twoPhases().at(0);   // radius 200

        assertTrue(state.isOutside(250));
        assertFalse(state.isOutside(150));
        assertFalse(state.isOutside(200), "exactly on the ring is safe");
    }

    @Test
    void refusesAStormThatGrows() {
        assertThrows(IllegalArgumentException.class, () -> new StormSchedule(100, List.of(
                new StormPhase(10, 10, 150, 1))));   // 150 > 100: a ring cannot open outward
    }

    @Test
    void refusesNonsenseTimings() {
        assertThrows(IllegalArgumentException.class, () -> new StormSchedule(100, List.of(
                new StormPhase(-1, 10, 50, 1))));
        assertThrows(IllegalArgumentException.class, () -> new StormSchedule(100, List.of(
                new StormPhase(10, 0, 50, 1))));   // a close must take time
    }

    @Test
    void refusesAStormWithNoPhases() {
        assertThrows(IllegalArgumentException.class, () -> new StormSchedule(100, List.of()));
    }
}

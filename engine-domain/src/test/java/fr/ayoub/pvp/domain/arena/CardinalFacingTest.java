package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snapping a player's look to one of the four straight directions.
 *
 * <p>A spawn should face dead-on down an axis — 0, 90, 180 or 270 — not 47°, so two fighters
 * squaring up actually look at each other and at the arena, not off at a corner. The admin marks a
 * spawn by standing and looking roughly the right way; this rounds that to the nearest of the four.
 *
 * <p>Minecraft yaw: 0 is south (+Z), 90 west (−X), 180 north (−Z), 270 east (+X). Opposite is what
 * makes two teams face each other — 0 against 180, 90 against 270.
 */
class CardinalFacingTest {

    @Test
    void snapsToTheNearestQuarter() {
        assertEquals(0f, CardinalFacing.snap(0));
        assertEquals(0f, CardinalFacing.snap(10));
        assertEquals(0f, CardinalFacing.snap(44));
        assertEquals(90f, CardinalFacing.snap(46));
        assertEquals(90f, CardinalFacing.snap(90));
        assertEquals(180f, CardinalFacing.snap(179));
        assertEquals(180f, CardinalFacing.snap(200));
        assertEquals(270f, CardinalFacing.snap(260));
    }

    @Test
    void wrapsAround() {
        assertEquals(0f, CardinalFacing.snap(359));
        assertEquals(0f, CardinalFacing.snap(360));
        assertEquals(90f, CardinalFacing.snap(450));
    }

    /** Bukkit hands out yaw in −180..180, so negatives have to land right. */
    @Test
    void handlesNegativeYaw() {
        assertEquals(270f, CardinalFacing.snap(-90));
        assertEquals(180f, CardinalFacing.snap(-180));
        assertEquals(0f, CardinalFacing.snap(-10));
        assertEquals(0f, CardinalFacing.snap(-359));
    }

    @Test
    void opposesAcrossTheAxis() {
        assertEquals(180f, CardinalFacing.opposite(0));
        assertEquals(270f, CardinalFacing.opposite(90));
        assertEquals(0f, CardinalFacing.opposite(180));
        assertEquals(90f, CardinalFacing.opposite(270));
    }

    /** Snapping is idempotent — snapping an already-straight yaw changes nothing. */
    @Test
    void leavesAStraightYawAlone() {
        for (float cardinal : new float[]{0, 90, 180, 270}) {
            assertEquals(cardinal, CardinalFacing.snap(cardinal));
        }
    }
}

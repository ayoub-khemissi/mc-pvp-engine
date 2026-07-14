package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the islands and the voting plains sit, and — the whole point — <b>how far apart</b>.
 *
 * <p>Three things must never be visible from each other, and every one of them has already gone
 * wrong at least once:
 *
 * <ul>
 *   <li><b>The plain and the island.</b> A team standing on its voting plain must not be able to
 *       read the map it is about to fight on, and a player in the match must not see a slab of
 *       leftover scenery floating over their head.
 *   <li><b>The two plains.</b> They started 36 blocks apart, which let a team stand on its own
 *       plain and read the enemy's three fortresses like a menu — the entire point of a secret
 *       vote, gone.
 *   <li><b>Two islands.</b> Two matches running at once are two matches, not a spectator sport.
 * </ul>
 *
 * <p>Every one of those was fixed by picking a bigger number by hand, against the view distance
 * <em>of the day</em>. Then the view distance was raised for an unrelated reason and two of them
 * silently broke again — because the numbers never knew what they depended on. So now they do:
 * the layout is handed the sight radius and <b>refuses to exist</b> if anything a player must not
 * see is inside it. A wall is not an option; a spectator flies through walls. Distance is the
 * only thing that actually works, and in a void world distance is free.
 */
class IslandLayoutTest {

    /** The numbers the mode actually ships, at a 10-chunk view distance. */
    private static IslandLayout shipped() {
        return new IslandLayout(128, 512, 512, 1024, 68, IslandLayout.sightOf(10));
    }

    @Test
    void sightIsTheViewDistancePlusASliceOfSlop() {
        // Chunks, not blocks: a player at the edge of their chunk still gets N chunks beyond it,
        // and the thing they must not see may start one block inside the last one.
        assertTrue(IslandLayout.sightOf(10) > 10 * 16);
    }

    @Test
    void theShippedLayoutKeepsEverythingOutOfSight() {
        IslandLayout layout = shipped();

        assertTrue(layout.gapBetweenIslands() >= layout.sight());
        assertTrue(layout.gapFromIslandToPlain() >= layout.sight());
        assertTrue(layout.gapBetweenPlains() >= layout.sight());
    }

    @Test
    void placesEachInstanceOnItsOwnSpacing() {
        IslandLayout layout = shipped();

        assertEquals(0, layout.originX(0));
        assertEquals(512, layout.originX(1));
        assertEquals(1024, layout.originX(2));
    }

    @Test
    void putsEachTeamOnItsOwnPlain() {
        IslandLayout layout = shipped();

        assertEquals(512, layout.votePlainZ(0));
        assertEquals(1024, layout.votePlainZ(1));
    }

    /** Two matches at once are two matches. This is the one nobody notices while testing alone. */
    @Test
    void refusesToLetTwoInstancesSeeEachOther() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new IslandLayout(128, 256, 512, 1024, 68, IslandLayout.sightOf(10)));

        assertTrue(thrown.getMessage().contains("instance"), thrown.getMessage());
    }

    /** The bug that was reported: the voting plain hanging in the sky over a live match. */
    @Test
    void refusesToLetTheMatchSeeTheVotingPlain() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new IslandLayout(128, 512, 300, 1024, 68, IslandLayout.sightOf(10)));

        assertTrue(thrown.getMessage().contains("plain"), thrown.getMessage());
    }

    /** A vote you can spy on is not a vote. */
    @Test
    void refusesToLetOneTeamSeeTheOthersFortresses() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new IslandLayout(128, 512, 512, 640, 68, IslandLayout.sightOf(10)));

        assertTrue(thrown.getMessage().contains("team"), thrown.getMessage());
    }

    /**
     * The layout that shipped before the view distance was raised. It was correct at 6 chunks
     * and quietly wrong at 10 — which is the entire reason this class exists.
     */
    @Test
    void catchesTheLayoutThatTheViewDistanceBrokeUnderUs() {
        assertThrows(IllegalArgumentException.class,
                () -> new IslandLayout(128, 256, 300, 600, 68, IslandLayout.sightOf(10)),
                "the old numbers must not pass at the new view distance");
    }

    /** A bigger fortress makes the plain deeper, which eats into the gap behind it. */
    @Test
    void accountsForHowDeepThePlainActuallyIs() {
        assertThrows(IllegalArgumentException.class,
                () -> new IslandLayout(128, 512, 512, 1024, 400, IslandLayout.sightOf(10)),
                "a plain deep enough to reach the other one must be refused");
    }
}

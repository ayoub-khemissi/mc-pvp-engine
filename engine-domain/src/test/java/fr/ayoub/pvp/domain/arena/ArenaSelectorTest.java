package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Picking which map a match is played on.
 *
 * Like Clash Royale: the arena you play in depends on your rating.
 */
class ArenaSelectorTest {

    private static MapDescriptor map(String id, Set<String> modes, int minRating, int maxRating) {
        return new MapDescriptor(id, modes, minRating, maxRating);
    }

    @Test
    void candidatesAreEveryEligibleMapNotJustTheBestOne() {
        // Fifteen any-rating duel arenas: all fifteen are candidates, so a caller can pick at random.
        List<MapDescriptor> maps = List.of(
                map("a", Set.of("duel"), 0, Integer.MAX_VALUE),
                map("b", Set.of("duel"), 0, Integer.MAX_VALUE),
                map("c", Set.of("dodgeball"), 0, Integer.MAX_VALUE));

        List<MapDescriptor> candidates = ArenaSelector.candidates(maps, "duel", 1000, false);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(m -> m.id().equals("a")));
        assertTrue(candidates.stream().anyMatch(m -> m.id().equals("b")));
    }

    @Test
    void candidatesNarrowToTheRatingBandWhenOneFits() {
        List<MapDescriptor> maps = List.of(
                map("bronze", Set.of("duel"), 0, 999),
                map("gold", Set.of("duel"), 1000, 1999),
                map("generic", Set.of("duel"), 0, Integer.MAX_VALUE));

        // A 1500 player: gold and generic both fit; bronze does not.
        List<MapDescriptor> candidates = ArenaSelector.candidates(maps, "duel", 1500, false);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().noneMatch(m -> m.id().equals("bronze")));
    }

    @Test
    void noCandidatesWhenNothingHostsTheMode() {
        List<MapDescriptor> maps = List.of(map("a", Set.of("dodgeball"), 0, Integer.MAX_VALUE));

        assertTrue(ArenaSelector.candidates(maps, "duel", 1000, false).isEmpty());
    }

    /** A map with no restrictions at all. */
    private static MapDescriptor anyMap(String id) {
        return new MapDescriptor(id, Set.of(), 0, Integer.MAX_VALUE);
    }

    // --- a mode that cannot be played on a general-purpose arena --------------------

    @Test
    void aModeThatNeedsItsOwnMapWillNotTakeAGeneralPurposeOne() {
        // The bug: the dev arenas name no mode, which means "any mode", so Fortress was
        // handed a duel island — a match that starts and can never be won.
        List<MapDescriptor> maps = List.of(anyMap("duel-island"));

        assertTrue(ArenaSelector.select(maps, "fortress", 1000, true).isEmpty());
        assertTrue(ArenaSelector.select(maps, "fortress", 1000).isPresent(),
                "a mode that does not ask for a dedicated map still plays on it");
    }

    @Test
    void aModeThatNeedsItsOwnMapTakesTheOneMadeForIt() {
        List<MapDescriptor> maps = List.of(
                anyMap("duel-island"),
                map("fortress-valley", Set.of("fortress"), 0, Integer.MAX_VALUE));

        assertEquals("fortress-valley",
                ArenaSelector.select(maps, "fortress", 1000, true).orElseThrow().id());
    }

    @Test
    void aDedicatedMapIsStillPickedByRating() {
        List<MapDescriptor> maps = List.of(
                map("fortress-bronze", Set.of("fortress"), 0, 1199),
                map("fortress-gold", Set.of("fortress"), 1200, 9999));

        assertEquals("fortress-gold",
                ArenaSelector.select(maps, "fortress", 1500, true).orElseThrow().id());
    }

    @Test
    void aMapDeclaresWhatItIsFor() {
        MapDescriptor general = anyMap("anything");
        MapDescriptor fortress = map("valley", Set.of("fortress"), 0, 9999);

        assertTrue(general.supports("fortress"), "no modes listed means it objects to none");
        assertFalse(general.isDedicatedTo("fortress"), "but it was not built for one either");
        assertTrue(fortress.isDedicatedTo("fortress"));
        assertFalse(fortress.isDedicatedTo("duel"));
    }

    @Test
    void nothingAvailableMeansNoMap() {
        assertTrue(ArenaSelector.select(List.of(), "duel", 1000).isEmpty());
    }

    @Test
    void aMapWithoutRestrictionsFitsAnyModeAndAnyRating() {
        Optional<MapDescriptor> chosen = ArenaSelector.select(List.of(anyMap("plain")), "dodgeball", 2500);

        assertEquals("plain", chosen.orElseThrow().id());
    }

    @Test
    void aMapOnlyServesTheModesItDeclares() {
        List<MapDescriptor> maps = List.of(map("ball-court", Set.of("dodgeball"), 0, 9999));

        assertTrue(ArenaSelector.select(maps, "duel", 1000).isEmpty(),
                "a dodgeball court must not host a duel");
    }

    @Test
    void theRatingDecidesTheArena() {
        List<MapDescriptor> maps = List.of(
                map("bronze", Set.of("duel"), 0, 1199),
                map("gold", Set.of("duel"), 1200, 1599),
                map("legend", Set.of("duel"), 1600, 9999));

        assertEquals("bronze", ArenaSelector.select(maps, "duel", 900).orElseThrow().id());
        assertEquals("gold", ArenaSelector.select(maps, "duel", 1300).orElseThrow().id());
        assertEquals("legend", ArenaSelector.select(maps, "duel", 2000).orElseThrow().id());
    }

    @Test
    void theBandEdgesAreInclusive() {
        MapDescriptor gold = map("gold", Set.of("duel"), 1200, 1599);

        assertTrue(gold.fits(1200));
        assertTrue(gold.fits(1599));
        assertFalse(gold.fits(1600));
    }

    @Test
    void aPlayerOutsideEveryBandGetsTheClosestArena() {
        List<MapDescriptor> maps = List.of(
                map("bronze", Set.of("duel"), 0, 1199),
                map("legend", Set.of("duel"), 1600, 9999));

        // 1400 fits neither: bronze is 201 away, legend is 200 away
        assertEquals("legend", ArenaSelector.select(maps, "duel", 1400).orElseThrow().id());
    }

    @Test
    void theMostSpecificBandWins() {
        // both accept 1300, but 'gold' is clearly meant for it
        List<MapDescriptor> maps = List.of(
                anyMap("generic"),
                map("gold", Set.of("duel"), 1200, 1599));

        assertEquals("gold", ArenaSelector.select(maps, "duel", 1300).orElseThrow().id());
    }

    @Test
    void aMatchNeverFailsJustBecauseNoBandFits() {
        // nobody covers 3000, but a duel map is free — play there rather than not play
        List<MapDescriptor> maps = List.of(map("bronze", Set.of("duel"), 0, 1199));

        assertEquals("bronze", ArenaSelector.select(maps, "duel", 3000).orElseThrow().id(),
                "the rating band is a preference, not a reason to cancel the match");
    }

    @Test
    void theFallbackStillRespectsTheMode() {
        List<MapDescriptor> maps = List.of(map("ball-court", Set.of("dodgeball"), 0, 1199));

        assertTrue(ArenaSelector.select(maps, "duel", 3000).isEmpty(),
                "the mode is a hard rule, unlike the rating");
    }

    @Test
    void theChoiceIsStableForTheSameInput() {
        List<MapDescriptor> maps = List.of(
                map("a", Set.of("duel"), 0, 2000),
                map("b", Set.of("duel"), 0, 2000));

        assertEquals(
                ArenaSelector.select(maps, "duel", 1000).orElseThrow().id(),
                ArenaSelector.select(maps, "duel", 1000).orElseThrow().id());
    }
}

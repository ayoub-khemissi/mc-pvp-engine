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

    /** A map with no restrictions at all. */
    private static MapDescriptor anyMap(String id) {
        return new MapDescriptor(id, Set.of(), 0, Integer.MAX_VALUE);
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

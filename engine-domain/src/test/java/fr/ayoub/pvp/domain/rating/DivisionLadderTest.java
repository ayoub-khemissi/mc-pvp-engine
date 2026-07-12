package fr.ayoub.pvp.domain.rating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivisionLadderTest {

    private final DivisionLadder ladder = DivisionLadder.standard();

    @Test
    void aBeginnerIsBronze() {
        assertEquals("bronze", ladder.of(0).id());
        assertEquals("bronze", ladder.of(999).id());
    }

    @Test
    void theThresholdIsTheFirstRatingOfTheDivision() {
        assertEquals("silver", ladder.of(1000).id());
        assertEquals("gold", ladder.of(1200).id());
    }

    @Test
    void theBestPlayersAreGrandmaster() {
        assertEquals("grandmaster", ladder.of(2000).id());
        assertEquals("grandmaster", ladder.of(9999).id());
    }

    @Test
    void aRatingBelowTheLadderStillHasADivision() {
        assertEquals("bronze", ladder.of(-500).id(), "a division must always be found");
    }

    @Test
    void everyDivisionIsReachable() {
        for (Division division : ladder.divisions()) {
            assertEquals(division.id(), ladder.of(division.minRating()).id());
        }
    }

    @Test
    void theNextDivisionTellsYouWhatToAimFor() {
        assertEquals("silver", ladder.next(950).orElseThrow().id());
        assertTrue(ladder.next(5000).isEmpty(), "there is nothing above the top division");
    }

    @Test
    void aLadderMustStartAtOrBelowZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new DivisionLadder(java.util.List.of(new Division("gold", "Gold", 1200))));
    }
}

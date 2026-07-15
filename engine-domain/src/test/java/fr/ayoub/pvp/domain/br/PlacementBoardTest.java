package fr.ayoub.pvp.domain.br;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Where each team finished, worked out from the order they were knocked out.
 *
 * <p>In a battle royale nobody hands you a placement — you earn it by outliving people. The first
 * team wiped in a field of 24 came 24th; the team wiped last came 2nd; the one still standing came
 * 1st. So all the board needs is the <b>order</b> of elimination, and every placement falls out of
 * it. This is the number the placement Elo and the HUD's "top N" both read.
 */
class PlacementBoardTest {

    @Test
    void theFirstOutIsLast() {
        PlacementBoard board = new PlacementBoard(24);
        board.eliminate(5);

        assertEquals(24, board.placementOf(5));
    }

    @Test
    void placementsCountDownInEliminationOrder() {
        PlacementBoard board = new PlacementBoard(4);
        board.eliminate(2);   // out first  -> 4th
        board.eliminate(0);   // out second -> 3rd
        board.eliminate(3);   // out third  -> 2nd

        assertEquals(4, board.placementOf(2));
        assertEquals(3, board.placementOf(0));
        assertEquals(2, board.placementOf(3));
    }

    @Test
    void theLastTeamStandingIsFirst() {
        PlacementBoard board = new PlacementBoard(4);
        board.eliminate(2);
        board.eliminate(0);
        board.eliminate(3);

        assertEquals(1, board.placementOf(1), "team 1 never went out");
    }

    /** A match that ends with several teams still alive: they tie for the top spot left. */
    @Test
    void survivorsTieForTheBestRemainingPlacement() {
        PlacementBoard board = new PlacementBoard(10);
        for (int t = 0; t < 6; t++) {
            board.eliminate(t);   // six out, four left
        }

        // Four survivors: 6,7,8,9 — all "top 4".
        assertEquals(4, board.placementOf(6));
        assertEquals(4, board.placementOf(9));
        // The last one eliminated is just behind them.
        assertEquals(5, board.placementOf(5));
    }

    @Test
    void eliminatingATeamTwiceDoesNotMoveIt() {
        PlacementBoard board = new PlacementBoard(4);
        board.eliminate(2);
        board.eliminate(0);
        board.eliminate(2);   // already out — ignored

        assertEquals(4, board.placementOf(2));
        assertEquals(3, board.placementOf(0));
    }

    @Test
    void countsHowManyAreStillIn() {
        PlacementBoard board = new PlacementBoard(4);
        assertEquals(4, board.remaining());
        board.eliminate(0);
        assertEquals(3, board.remaining());
    }

    @Test
    void refusesATeamOutsideTheField() {
        PlacementBoard board = new PlacementBoard(4);
        assertThrows(IndexOutOfBoundsException.class, () -> board.eliminate(4));
        assertThrows(IndexOutOfBoundsException.class, () -> board.placementOf(-1));
    }

    @Test
    void refusesAFieldOfLessThanTwo() {
        assertThrows(IllegalArgumentException.class, () -> new PlacementBoard(1));
    }
}

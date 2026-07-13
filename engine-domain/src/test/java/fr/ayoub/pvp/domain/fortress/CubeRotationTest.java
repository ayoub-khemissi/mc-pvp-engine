package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Turning a fortress round.
 *
 * The two fortresses in a match <b>face each other</b>. A blueprint's front is its
 * {@code z = 0} face, so one of the two has to be turned half way round — gate towards the
 * enemy, blind wall behind. Get this wrong by one block and every fortress is pasted a
 * block off its pad; get it wrong by a sign and one team fights a fortress that has its back
 * to them.
 */
class CubeRotationTest {

    private static final int SIZE = 20;

    @Test
    void noRotationChangesNothing() {
        assertEquals(new BlockPos(3, 7, 5), CubeRotation.NONE.apply(new BlockPos(3, 7, 5), SIZE));
    }

    @Test
    void aHalfTurnSendsACornerToTheOppositeCorner() {
        assertEquals(new BlockPos(19, 0, 19), CubeRotation.HALF_TURN.apply(new BlockPos(0, 0, 0), SIZE));
        assertEquals(new BlockPos(0, 0, 0), CubeRotation.HALF_TURN.apply(new BlockPos(19, 0, 19), SIZE));
    }

    @Test
    void aHalfTurnSendsTheFrontFaceToTheBack() {
        // z = 0 is the front. After half a turn it must be the far face.
        assertEquals(19, CubeRotation.HALF_TURN.apply(new BlockPos(5, 3, 0), SIZE).z());
        assertEquals(0, CubeRotation.HALF_TURN.apply(new BlockPos(5, 3, 19), SIZE).z());
    }

    @Test
    void heightIsNeverTouched() {
        for (CubeRotation rotation : CubeRotation.values()) {
            assertEquals(7, rotation.apply(new BlockPos(2, 7, 11), SIZE).y(),
                    rotation + " must not move a block up or down");
        }
    }

    @Test
    void aQuarterTurnIsAQuarterTurn() {
        // Clockwise looking down: +X (east) becomes +Z (south).
        assertEquals(new BlockPos(19, 0, 0), CubeRotation.CLOCKWISE_90.apply(new BlockPos(0, 0, 0), SIZE));
        assertEquals(new BlockPos(19, 0, 19), CubeRotation.CLOCKWISE_90.apply(new BlockPos(19, 0, 0), SIZE));
    }

    @Test
    void fourQuarterTurnsComeBackToTheStart() {
        BlockPos start = new BlockPos(3, 9, 14);
        BlockPos pos = start;

        for (int i = 0; i < 4; i++) {
            pos = CubeRotation.CLOCKWISE_90.apply(pos, SIZE);
        }
        assertEquals(start, pos);
    }

    @Test
    void twoHalfTurnsComeBackToTheStart() {
        BlockPos start = new BlockPos(3, 9, 14);

        assertEquals(start, CubeRotation.HALF_TURN.apply(
                CubeRotation.HALF_TURN.apply(start, SIZE), SIZE));
    }

    @Test
    void everyRotationKeepsEveryBlockInsideTheCube() {
        // The bug this catches is an off-by-one that pastes a fortress one block off its pad
        // — or worse, one block outside it, into whatever was there before.
        for (CubeRotation rotation : CubeRotation.values()) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    BlockPos turned = rotation.apply(new BlockPos(x, 0, z), SIZE);

                    assertEquals(true, turned.x() >= 0 && turned.x() < SIZE,
                            rotation + " sent x out of the cube: " + turned);
                    assertEquals(true, turned.z() >= 0 && turned.z() < SIZE,
                            rotation + " sent z out of the cube: " + turned);
                }
            }
        }
    }
}

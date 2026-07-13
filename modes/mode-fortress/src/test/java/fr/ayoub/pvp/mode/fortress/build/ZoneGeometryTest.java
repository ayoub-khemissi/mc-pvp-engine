package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.domain.fortress.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneGeometryTest {

    /** Room 50, cube 20 — so the cube is inset by 15 on each side. */
    private final ZoneGeometry zone = new ZoneGeometry(0, 0, 64, 0, 50, 20);

    @Test
    void theCubeIsCentredInTheRoom() {
        assertEquals(15, zone.pad());
        assertEquals(15, zone.cubeX());
        assertEquals(15, zone.cubeZ());
    }

    @Test
    void theCubeSitsOnTheFloorNotInIt() {
        // The floor is the room's bottom layer. The cube's first block is one above it,
        // or a fortress would be pasted with its ground floor buried.
        assertEquals(65, zone.cubeY());
    }

    @Test
    void aZoneIsOffsetByItsIndex() {
        ZoneGeometry third = new ZoneGeometry(2, 256, 64, 0, 50, 20);

        assertEquals(256 + 15, third.cubeX());
        assertEquals(15, third.cubeZ(), "zones are spaced along X only");
    }

    @Test
    void worldCoordinatesBecomeBlueprintCoordinates() {
        assertEquals(new BlockPos(0, 0, 0), zone.toBlueprint(15, 65, 15));
        assertEquals(new BlockPos(19, 19, 19), zone.toBlueprint(34, 84, 34));
        assertEquals(new BlockPos(5, 2, 7), zone.toBlueprint(20, 67, 22));
    }

    @Test
    void blueprintCoordinatesBecomeWorldCoordinates() {
        assertArrayEqualsInt(new int[]{15, 65, 15}, zone.toWorld(new BlockPos(0, 0, 0)));
        assertArrayEqualsInt(new int[]{34, 84, 34}, zone.toWorld(new BlockPos(19, 19, 19)));
    }

    @Test
    void theTwoConversionsAreEachOthersOpposite() {
        // The one bug that matters here is a fortress pasted one block off.
        for (int x = 0; x < 20; x += 7) {
            for (int y = 0; y < 20; y += 7) {
                for (int z = 0; z < 20; z += 7) {
                    BlockPos original = new BlockPos(x, y, z);
                    int[] world = zone.toWorld(original);

                    assertEquals(original, zone.toBlueprint(world[0], world[1], world[2]));
                }
            }
        }
    }

    @Test
    void whatIsInTheCubeAndWhatIsNot() {
        assertTrue(zone.isInCube(15, 65, 15), "the corner block");
        assertTrue(zone.isInCube(34, 84, 34), "the far corner");

        assertFalse(zone.isInCube(14, 65, 15), "one block west of the cube");
        assertFalse(zone.isInCube(35, 65, 15), "one block east of it");
        assertFalse(zone.isInCube(15, 64, 15), "the floor is not in the cube");
        assertFalse(zone.isInCube(15, 85, 15), "one block above the cube");
    }

    @Test
    void theRoomIsBiggerThanTheCube() {
        assertTrue(zone.isInRoom(0, 64, 0), "the room's own corner");
        assertTrue(zone.isInRoom(49, 113, 49), "the far corner of a 50-cube room");
        assertTrue(zone.isInRoom(20, 70, 20), "the cube is inside the room");

        assertFalse(zone.isInRoom(-1, 64, 0), "outside the west wall");
        assertFalse(zone.isInRoom(50, 64, 0), "outside the east wall");
        assertFalse(zone.isInRoom(0, 63, 0), "below the floor");
        assertFalse(zone.isInRoom(0, 114, 0), "above the ceiling");
    }

    @Test
    void aRoomMustHoldItsCube() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZoneGeometry(0, 0, 64, 0, 20, 20),
                "a cube exactly as wide as the room leaves no room to stand in");
        assertThrows(IllegalArgumentException.class,
                () -> new ZoneGeometry(0, 0, 64, 0, 10, 20));
    }

    @Test
    void everythingScalesWithTheConfig() {
        // The whole point: 20 and 50 are settings, not truths.
        ZoneGeometry small = new ZoneGeometry(0, 0, 100, 0, 16, 8);

        assertEquals(4, small.pad());
        assertEquals(4, small.cubeX());
        assertEquals(101, small.cubeY());
        assertTrue(small.isInCube(4, 101, 4));
        assertFalse(small.isInCube(12, 101, 4), "the cube ends at 8 blocks");
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "x");
        assertEquals(expected[1], actual[1], "y");
        assertEquals(expected[2], actual[2], "z");
    }
}

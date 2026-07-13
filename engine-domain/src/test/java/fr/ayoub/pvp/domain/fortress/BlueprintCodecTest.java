package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintCodecTest {

    private static Blueprint roundTrip(Blueprint original) {
        return BlueprintCodec.decode(BlueprintCodec.encode(original));
    }

    @Test
    void anEmptyBlueprintSurvivesTheRoundTrip() {
        Blueprint decoded = roundTrip(new Blueprint(20));

        assertEquals(20, decoded.size());
        assertEquals(0, decoded.blockCount());
        assertNull(decoded.crystal());
    }

    @Test
    void everyBlockComesBackWhereItWas() {
        Blueprint original = new Blueprint(20);
        original.set(0, 0, 0, "STONE");
        original.set(19, 19, 19, "OBSIDIAN");
        original.set(7, 3, 11, "OAK_PLANKS");
        original.crystal(new BlockPos(10, 5, 10));

        Blueprint decoded = roundTrip(original);

        assertEquals("STONE", decoded.get(0, 0, 0));
        assertEquals("OBSIDIAN", decoded.get(19, 19, 19));
        assertEquals("OAK_PLANKS", decoded.get(7, 3, 11));
        assertEquals(Blueprint.AIR, decoded.get(1, 1, 1));
        assertEquals(new BlockPos(10, 5, 10), decoded.crystal());
        assertEquals(original.counts(), decoded.counts());
    }

    @Test
    void theCubeSizeIsCarriedWithTheData() {
        // A fortress saved when the cube was 12 must still load when the config says 20.
        Blueprint decoded = roundTrip(new Blueprint(12));

        assertEquals(12, decoded.size());
    }

    @Test
    void aBlueprintWithNoCrystalIsFine() {
        Blueprint original = new Blueprint(10);
        original.set(1, 1, 1, "STONE");

        assertNull(roundTrip(original).crystal(), "a draft has no crystal yet");
    }

    @Test
    void aSolidCubeSurvives() {
        Blueprint original = new Blueprint(10);
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 10; z++) {
                    original.set(x, y, z, "STONE");
                }
            }
        }

        Blueprint decoded = roundTrip(original);

        assertEquals(1000, decoded.blockCount());
        assertEquals("STONE", decoded.get(5, 5, 5));
    }

    @Test
    void theSameBlueprintAlwaysEncodesTheSameWay() {
        Blueprint original = new Blueprint(8);
        original.set(1, 2, 3, "STONE");
        original.crystal(new BlockPos(4, 4, 4));

        assertArrayEquals(BlueprintCodec.encode(original), BlueprintCodec.encode(original),
                "encoding must be deterministic, or every save looks like a change");
    }

    @Test
    void anEmptyFortressIsTiny() {
        // 8000 cells of air must not become 8000 bytes in the database.
        byte[] encoded = BlueprintCodec.encode(new Blueprint(20));

        assertTrue(encoded.length < 500,
                "an empty 20³ fortress compressed to " + encoded.length + " bytes");
    }

    @Test
    void garbageIsRejectedRatherThanMisread() {
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintCodec.decode(new byte[]{1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintCodec.decode(new byte[0]));
    }

    @Test
    void aFutureFormatIsRefusedRatherThanGuessedAt() {
        byte[] encoded = BlueprintCodec.encode(new Blueprint(10));
        encoded[4] = 99;   // the version byte, right after the magic

        assertThrows(IllegalArgumentException.class, () -> BlueprintCodec.decode(encoded));
    }
}

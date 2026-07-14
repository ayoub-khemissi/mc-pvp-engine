package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block position in one long.
 *
 * The journal keeps one entry per block a match changed, and there can be a hundred thousand
 * of them. Three ints in an object is three times the memory and a hash on every lookup, so
 * the position is packed — and, unlike the first version of this, it can be <b>unpacked</b>:
 * the journal now has to find the block again to put it back.
 */
class BlockKeyTest {

    @Test
    void roundTripsTheOrigin() {
        long key = BlockKey.of(0, 0, 0);

        assertEquals(0, BlockKey.x(key));
        assertEquals(0, BlockKey.y(key));
        assertEquals(0, BlockKey.z(key));
    }

    @Test
    void roundTripsNegativeCoordinates() {
        long key = BlockKey.of(-1234, -17, -9876);

        assertEquals(-1234, BlockKey.x(key));
        assertEquals(-17, BlockKey.y(key));
        assertEquals(-9876, BlockKey.z(key));
    }

    /**
     * The whole legal height of a modern world. The old encoding masked y to 12 unsigned bits,
     * which turned bedrock at y = -64 into y = 4032 — it never showed because nothing ever
     * read a key back.
     */
    @Test
    void roundTripsTheFullWorldHeight() {
        for (int y = -64; y <= 320; y++) {
            long key = BlockKey.of(7, y, -7);
            assertEquals(y, BlockKey.y(key), "y = " + y);
        }
    }

    @Test
    void roundTripsTheEdgesOfTheWorldBorder() {
        int far = 30_000_000;

        long key = BlockKey.of(far, 319, -far);

        assertEquals(far, BlockKey.x(key));
        assertEquals(319, BlockKey.y(key));
        assertEquals(-far, BlockKey.z(key));
    }

    /** Two different blocks must never collide, or a restore would skip one of them. */
    @Test
    void givesEveryBlockItsOwnKey() {
        Set<Long> keys = new HashSet<>();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    assertTrue(keys.add(BlockKey.of(x, y, z)), "collision at " + x + "," + y + "," + z);
                }
            }
        }

        assertEquals(7 * 7 * 7, keys.size());
    }

    /**
     * Out of range is a bug, and a bug must be loud. Silently wrapping around would restore a
     * block somewhere else on the map — which is worse than not restoring it at all.
     */
    @Test
    void refusesAPositionItCannotHold() {
        assertThrows(IllegalArgumentException.class, () -> BlockKey.of(0, 2048, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockKey.of(0, -2049, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockKey.of(1 << 26, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockKey.of(0, 0, -(1 << 26) - 1));
    }
}

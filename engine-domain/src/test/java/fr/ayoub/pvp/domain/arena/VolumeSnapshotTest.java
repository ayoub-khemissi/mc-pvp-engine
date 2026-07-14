package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A photograph of a piece of the map, taken while it is still whole.
 *
 * <p><b>Why a photograph and not an undo log.</b> The engine used to remember every block a match
 * changed and put those back afterwards. That can only ever undo <em>what it saw</em>: it lived in
 * memory, so a server that was killed rather than stopped lost it, and any change that arrived
 * through an event nobody had hooked was invisible to it. Miss one, once, and the damage is
 * permanent — which is how a tree cut down three matches ago was still lying there.
 *
 * <p>A snapshot cannot have that bug, because it does not care what happened. It knows what the
 * map is <b>supposed</b> to be, and it puts that back. It also works on a map it did not build,
 * which is the whole point: when a designer hands over an arena, the engine will not know how to
 * regenerate it, and it will not have to.
 *
 * <p>It is a palette plus one index per block, because an arena is overwhelmingly the same handful
 * of blocks repeated a million times: a 128×128 island is nine hundred thousand cells and perhaps
 * fifty distinct states.
 */
class VolumeSnapshotTest {

    @Test
    void remembersWhatWasWhere() {
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(2, 2, 2);
        builder.set(0, 0, 0, "minecraft:stone");
        builder.set(1, 1, 1, "minecraft:oak_stairs[facing=east]");

        VolumeSnapshot snapshot = builder.build();

        assertEquals("minecraft:stone", snapshot.at(0, 0, 0));
        assertEquals("minecraft:oak_stairs[facing=east]", snapshot.at(1, 1, 1));
    }

    /** Everything nobody set is air. An arena is mostly air, and air is not a special case. */
    @Test
    void treatsWhatWasNeverSetAsAir() {
        VolumeSnapshot snapshot = VolumeSnapshot.builder(2, 2, 2).build();

        assertEquals(VolumeSnapshot.AIR, snapshot.at(1, 0, 1));
    }

    /**
     * The reason this is a palette at all. A million blocks of stone must cost a million
     * <b>indices</b>, not a million strings.
     */
    @Test
    void storesEachDistinctStateOnce() {
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(10, 1, 10);
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                builder.set(x, 0, z, "minecraft:stone");
            }
        }
        builder.set(5, 0, 5, "minecraft:diamond_ore");

        VolumeSnapshot snapshot = builder.build();

        assertEquals(100, snapshot.blocks());
        assertEquals(3, snapshot.palette().size(), "air, stone, diamond ore — and nothing else");
    }

    @Test
    void keepsTheFullBlockState() {
        // Not the type. A staircase facing the wrong way is a different arena.
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(1, 1, 2);
        builder.set(0, 0, 0, "minecraft:oak_stairs[facing=east,half=bottom]");
        builder.set(0, 0, 1, "minecraft:oak_stairs[facing=west,half=bottom]");

        VolumeSnapshot snapshot = builder.build();

        assertEquals(2 + 1, snapshot.palette().size());
        assertEquals("minecraft:oak_stairs[facing=west,half=bottom]", snapshot.at(0, 0, 1));
    }

    @Test
    void refusesAPositionOutsideTheVolume() {
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(2, 2, 2);

        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(2, 0, 0, "minecraft:stone"));
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(0, -1, 0, "minecraft:stone"));

        VolumeSnapshot snapshot = builder.build();
        assertThrows(IndexOutOfBoundsException.class, () -> snapshot.at(0, 0, 2));
    }

    @Test
    void refusesAVolumeWithNothingInIt() {
        assertThrows(IllegalArgumentException.class, () -> VolumeSnapshot.builder(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> VolumeSnapshot.builder(1, -1, 1));
    }

    // --- the file it becomes -------------------------------------------------------------

    @Test
    void survivesBeingWrittenAndReadBack() {
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(4, 3, 2);
        builder.set(0, 0, 0, "minecraft:bedrock");
        builder.set(3, 2, 1, "minecraft:chest[facing=north]");
        builder.set(1, 1, 1, "minecraft:stone");

        VolumeSnapshot before = builder.build();
        VolumeSnapshot after = VolumeSnapshotCodec.decode(VolumeSnapshotCodec.encode(before));

        assertEquals(before.sizeX(), after.sizeX());
        assertEquals(before.sizeY(), after.sizeY());
        assertEquals(before.sizeZ(), after.sizeZ());

        for (int x = 0; x < before.sizeX(); x++) {
            for (int y = 0; y < before.sizeY(); y++) {
                for (int z = 0; z < before.sizeZ(); z++) {
                    assertEquals(before.at(x, y, z), after.at(x, y, z), x + "," + y + "," + z);
                }
            }
        }
    }

    /**
     * An arena is millions of blocks and it has to sit on disk, and in memory, without being a
     * problem. It compresses to almost nothing because it is enormous stretches of the same thing.
     */
    @Test
    void compressesAnArenaSizedVolumeToSomethingSmall() {
        VolumeSnapshot.Builder builder = VolumeSnapshot.builder(64, 64, 64);
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                for (int y = 0; y < 32; y++) {
                    builder.set(x, y, z, "minecraft:stone");
                }
            }
        }

        byte[] bytes = VolumeSnapshotCodec.encode(builder.build());

        assertTrue(bytes.length < 20_000,
                "262144 blocks came to " + bytes.length + " bytes");
    }

    @Test
    void refusesBytesItDidNotWrite() {
        assertThrows(IllegalArgumentException.class,
                () -> VolumeSnapshotCodec.decode("not a snapshot".getBytes()));
    }
}

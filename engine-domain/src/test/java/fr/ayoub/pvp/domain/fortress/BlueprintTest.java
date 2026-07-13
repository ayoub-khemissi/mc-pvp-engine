package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTest {

    /** 20 today. It is a parameter, not a truth — the whole point of this test. */
    private final Blueprint blueprint = new Blueprint(20);

    @Test
    void aFreshBlueprintIsEmptyAir() {
        assertEquals(Blueprint.AIR, blueprint.get(0, 0, 0));
        assertEquals(Blueprint.AIR, blueprint.get(19, 19, 19));
        assertTrue(blueprint.counts().isEmpty(), "air is not counted");
        assertNull(blueprint.crystal());
    }

    @Test
    void theCubeCanBeAnySize() {
        Blueprint small = new Blueprint(8);

        assertEquals(8, small.size());
        assertEquals(Blueprint.AIR, small.get(7, 7, 7));
        assertThrows(IndexOutOfBoundsException.class, () -> small.get(8, 0, 0));
    }

    @Test
    void aCubeNeedsAPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> new Blueprint(0));
        assertThrows(IllegalArgumentException.class, () -> new Blueprint(-3));
    }

    @Test
    void aBlockIsRememberedWhereItWasPut() {
        blueprint.set(3, 4, 5, "STONE");

        assertEquals("STONE", blueprint.get(3, 4, 5));
        assertEquals(Blueprint.AIR, blueprint.get(3, 4, 6));
    }

    @Test
    void blocksAreCountedByType() {
        blueprint.set(0, 0, 0, "STONE");
        blueprint.set(1, 0, 0, "STONE");
        blueprint.set(2, 0, 0, "OBSIDIAN");

        assertEquals(2, blueprint.counts().get("STONE"));
        assertEquals(1, blueprint.counts().get("OBSIDIAN"));
    }

    @Test
    void removingABlockStopsCountingIt() {
        blueprint.set(0, 0, 0, "STONE");
        blueprint.set(0, 0, 0, Blueprint.AIR);

        assertTrue(blueprint.counts().isEmpty());
        assertEquals(Blueprint.AIR, blueprint.get(0, 0, 0));
    }

    @Test
    void replacingABlockRecountsIt() {
        blueprint.set(0, 0, 0, "STONE");
        blueprint.set(0, 0, 0, "OBSIDIAN");

        assertNull(blueprint.counts().get("STONE"));
        assertEquals(1, blueprint.counts().get("OBSIDIAN"));
    }

    @Test
    void theCrystalIsRememberedAsAPosition() {
        blueprint.crystal(new BlockPos(10, 5, 10));

        assertEquals(new BlockPos(10, 5, 10), blueprint.crystal());
    }

    @Test
    void nothingExistsOutsideTheCube() {
        assertThrows(IndexOutOfBoundsException.class, () -> blueprint.set(20, 0, 0, "STONE"));
        assertThrows(IndexOutOfBoundsException.class, () -> blueprint.set(-1, 0, 0, "STONE"));
        assertThrows(IndexOutOfBoundsException.class, () -> blueprint.get(0, 20, 0));
    }

    @Test
    void aPositionOutsideTheCubeIsNotContained() {
        assertTrue(blueprint.contains(new BlockPos(0, 0, 0)));
        assertTrue(blueprint.contains(new BlockPos(19, 19, 19)));
        assertFalse(blueprint.contains(new BlockPos(20, 0, 0)));
        assertFalse(blueprint.contains(new BlockPos(0, -1, 0)));
    }

    @Test
    void clearingWipesEverything() {
        blueprint.set(1, 1, 1, "STONE");
        blueprint.crystal(new BlockPos(2, 2, 2));

        blueprint.clear();

        assertTrue(blueprint.counts().isEmpty());
        assertNull(blueprint.crystal());
        assertEquals(Blueprint.AIR, blueprint.get(1, 1, 1));
    }

    @Test
    void aBlueprintCanBeCopied() {
        blueprint.set(1, 1, 1, "STONE");
        blueprint.crystal(new BlockPos(2, 2, 2));

        Blueprint copy = blueprint.copy();
        copy.set(1, 1, 1, "OBSIDIAN");

        assertEquals("STONE", blueprint.get(1, 1, 1), "the original is untouched");
        assertEquals("OBSIDIAN", copy.get(1, 1, 1));
        assertEquals(new BlockPos(2, 2, 2), copy.crystal());
    }

    @Test
    void howManyBlocksItUses() {
        blueprint.set(0, 0, 0, "STONE");
        blueprint.set(1, 0, 0, "STONE");
        blueprint.set(2, 0, 0, "OBSIDIAN");

        assertEquals(3, blueprint.blockCount());
    }
}

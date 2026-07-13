package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block in a blueprint is stored with its <b>state</b>, not just its name.
 *
 * A door is two halves, a stair faces a direction, a piston points somewhere. Store only
 * "OAK_STAIRS" and every stair in a pasted fortress faces east, and every door turns into
 * two identical bottom halves. The rules, though, are about the <b>type</b>: the obsidian
 * budget does not care which way the obsidian is facing.
 *
 * So the blueprint keeps the full state, and this is what reads the type back out of it.
 */
class BlockIdsTest {

    @Test
    void aPlainNameIsItsOwnType() {
        assertEquals("STONE", BlockIds.typeOf("STONE"));
        assertEquals("AIR", BlockIds.typeOf("AIR"));
    }

    @Test
    void theNamespaceIsDropped() {
        assertEquals("STONE", BlockIds.typeOf("minecraft:stone"));
    }

    @Test
    void theStateIsDropped() {
        assertEquals("OAK_STAIRS", BlockIds.typeOf(
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"));
    }

    @Test
    void aDoorKeepsItsTypeWhicheverHalfItIs() {
        String lower = "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]";
        String upper = "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]";

        assertEquals("OAK_DOOR", BlockIds.typeOf(lower));
        assertEquals(BlockIds.typeOf(lower), BlockIds.typeOf(upper),
                "both halves cost the same budget — they are the same block");
    }

    @Test
    void nothingIsAir() {
        assertEquals("AIR", BlockIds.typeOf(null));
        assertEquals("AIR", BlockIds.typeOf(""));
        assertEquals("AIR", BlockIds.typeOf("   "));
    }
}

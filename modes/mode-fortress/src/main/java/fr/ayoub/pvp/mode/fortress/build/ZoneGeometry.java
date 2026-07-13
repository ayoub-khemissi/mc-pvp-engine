package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.domain.fortress.BlockPos;

/**
 * Where the buildable cube sits inside a builder's room, in plain integers.
 *
 * The <b>only</b> place that knows this offset. Everything else asks it: "is that block in
 * the cube?", "where does this blueprint block go in the world?". Two pieces of code
 * computing the same offset is exactly how a fortress ends up pasted one block off, and
 * that bug is invisible until a crystal is buried in a wall.
 *
 * No Bukkit on purpose — it is arithmetic, so it is unit-tested rather than play-tested.
 *
 * <pre>
 *   room:  [x, x+room)            the walls, the floor, the ceiling
 *   cube:  [x+pad, x+pad+cube)    centred, sitting ON the floor
 * </pre>
 */
public record ZoneGeometry(int index, int roomX, int roomY, int roomZ, int roomSize, int cubeSize) {

    public ZoneGeometry {
        if (cubeSize < 1) {
            throw new IllegalArgumentException("a fortress needs a positive size, got " + cubeSize);
        }
        if (roomSize <= cubeSize) {
            throw new IllegalArgumentException("the room (" + roomSize
                    + ") must be bigger than the fortress (" + cubeSize + ") — the builder has "
                    + "to be able to walk around it");
        }
    }

    /** How far the cube is inset from the room's walls. */
    public int pad() {
        return (roomSize - cubeSize) / 2;
    }

    public int cubeX() {
        return roomX + pad();
    }

    /** One above the floor: the cube's ground layer must not be buried in it. */
    public int cubeY() {
        return roomY + 1;
    }

    public int cubeZ() {
        return roomZ + pad();
    }

    /** Blueprint → world, as {x, y, z}. */
    public int[] toWorld(BlockPos pos) {
        return new int[]{cubeX() + pos.x(), cubeY() + pos.y(), cubeZ() + pos.z()};
    }

    /** World → blueprint. Outside the cube, the result is simply out of range. */
    public BlockPos toBlueprint(int x, int y, int z) {
        return new BlockPos(x - cubeX(), y - cubeY(), z - cubeZ());
    }

    public boolean isInCube(int x, int y, int z) {
        BlockPos pos = toBlueprint(x, y, z);
        return inRange(pos.x()) && inRange(pos.y()) && inRange(pos.z());
    }

    private boolean inRange(int value) {
        return value >= 0 && value < cubeSize;
    }

    /** Inside the walls — the whole room, cube included. */
    public boolean isInRoom(int x, int y, int z) {
        return x >= roomX && x < roomX + roomSize
                && y >= roomY && y < roomY + roomSize
                && z >= roomZ && z < roomZ + roomSize;
    }
}

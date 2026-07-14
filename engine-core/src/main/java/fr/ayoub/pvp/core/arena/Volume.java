package fr.ayoub.pvp.core.arena;

import org.bukkit.Location;

/**
 * A box of the world that a match is allowed to wreck, and that the engine puts back.
 *
 * <p>It is deliberately <b>not</b> the arena's {@code bounds}. The bounds are the invisible wall —
 * where a player may go — and in Fortress that stretches a thousand blocks down the Z axis to
 * take in the voting plains, because a player standing on one must not be dragged back. Snapshotting
 * that would mean photographing eight million blocks of empty sky.
 *
 * <p>What has to be restored is only what can be <b>changed</b>: the island itself, and the small
 * platforms the fortresses are displayed on. So a map lists them, one box at a time, and the engine
 * photographs exactly those. A designer's map will do the same, and the engine will not need to
 * know anything else about it.
 */
public record Volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Volume {
        int lowX = Math.min(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);

        maxX = Math.max(minX, maxX);
        maxY = Math.max(minY, maxY);
        maxZ = Math.max(minZ, maxZ);

        minX = lowX;
        minY = lowY;
        minZ = lowZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long blocks() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(Location at) {
        return at.getBlockX() >= minX && at.getBlockX() <= maxX
                && at.getBlockY() >= minY && at.getBlockY() <= maxY
                && at.getBlockZ() >= minZ && at.getBlockZ() <= maxZ;
    }
}

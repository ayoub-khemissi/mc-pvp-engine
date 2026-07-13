package fr.ayoub.pvp.domain.fortress;

/** A block, in blueprint coordinates: 0,0,0 is the corner of the cube. */
public record BlockPos(int x, int y, int z) {

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}

package fr.ayoub.pvp.domain.arena;

/**
 * A block position packed into a single {@code long}.
 *
 * The arena journal holds one entry per block a match changed, and a long Fortress match
 * changes them by the hundred thousand. A three-int object per entry is three times the
 * memory and an object header on top of it, for a value that fits comfortably in 64 bits.
 *
 * <p>26 bits of x, 26 bits of z (±33 million — the world border is 30 million), and 12 bits
 * of y (±2048, where Minecraft only ever uses −64 to 320). All three are <b>signed</b>, and
 * all three can be read back: the journal has to find the block again to put it back.
 */
public final class BlockKey {

    private static final int HORIZONTAL_BITS = 26;
    private static final int VERTICAL_BITS = 12;

    private static final int MAX_HORIZONTAL = (1 << (HORIZONTAL_BITS - 1)) - 1;   // 33 554 431
    private static final int MAX_VERTICAL = (1 << (VERTICAL_BITS - 1)) - 1;       // 2047

    private BlockKey() {
    }

    public static long of(int x, int y, int z) {
        check(x, MAX_HORIZONTAL, "x");
        check(z, MAX_HORIZONTAL, "z");
        check(y, MAX_VERTICAL, "y");

        return ((long) x & 0x3FFFFFF) << 38
                | ((long) z & 0x3FFFFFF) << 12
                | ((long) y & 0xFFF);
    }

    /** The shifts sign-extend: left to put the field's top bit at 63, then arithmetic right. */
    public static int x(long key) {
        return (int) (key >> 38);
    }

    public static int z(long key) {
        return (int) (key << 26 >> 38);
    }

    public static int y(long key) {
        return (int) (key << 52 >> 52);
    }

    /**
     * A position we cannot hold is a bug, and a bug has to be loud. Wrapping around silently
     * would put a block back somewhere else on the map, which is far worse than not putting
     * it back at all.
     */
    private static void check(int value, int limit, String axis) {
        if (value > limit || value < -limit - 1) {
            throw new IllegalArgumentException(axis + " is out of range: " + value);
        }
    }
}

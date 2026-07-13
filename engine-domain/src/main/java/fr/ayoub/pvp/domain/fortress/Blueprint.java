package fr.ayoub.pvp.domain.fortress;

import java.util.HashMap;
import java.util.Map;

/**
 * A fortress, as data: a cube of block ids, plus where the Power Crystal stands.
 *
 * The cube's <b>size is a parameter</b> — 20 today, and everything about Fortress is still
 * an experiment. Nothing here may assume 20.
 *
 * Block ids are plain strings ("STONE", "OBSIDIAN"), not Bukkit Materials: this is the
 * domain, it must stay testable with no server. The crystal is a position rather than a
 * block, because in Minecraft it is an entity — the engine spawns it when the fortress is
 * pasted.
 *
 * Counts are kept up to date as blocks are placed, so checking a build against its budget
 * never has to walk 8000 cells.
 */
public final class Blueprint {

    public static final String AIR = "AIR";

    private final int size;
    private final String[] blocks;
    private final Map<String, Integer> counts = new HashMap<>();

    private BlockPos crystal;

    public Blueprint(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("a fortress needs a positive size, got " + size);
        }
        this.size = size;
        this.blocks = new String[size * size * size];
        java.util.Arrays.fill(blocks, AIR);
    }

    public int size() {
        return size;
    }

    public boolean contains(BlockPos pos) {
        return pos.x() >= 0 && pos.x() < size
                && pos.y() >= 0 && pos.y() < size
                && pos.z() >= 0 && pos.z() < size;
    }

    public String get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public String get(BlockPos pos) {
        return get(pos.x(), pos.y(), pos.z());
    }

    public void set(int x, int y, int z, String block) {
        int index = index(x, y, z);
        String previous = blocks[index];
        String next = block == null || block.isBlank() ? AIR : block;

        if (previous.equals(next)) {
            return;
        }

        decrement(previous);
        blocks[index] = next;
        increment(next);
    }

    public void set(BlockPos pos, String block) {
        set(pos.x(), pos.y(), pos.z(), block);
    }

    /** Where the Power Crystal stands, or null while the build has none. */
    public BlockPos crystal() {
        return crystal;
    }

    public void crystal(BlockPos pos) {
        this.crystal = pos;
    }

    /** How many of each block type the build uses. Air is not counted. */
    public Map<String, Integer> counts() {
        return Map.copyOf(counts);
    }

    /** Every block placed, all types together. */
    public int blockCount() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void clear() {
        java.util.Arrays.fill(blocks, AIR);
        counts.clear();
        crystal = null;
    }

    public Blueprint copy() {
        Blueprint copy = new Blueprint(size);
        System.arraycopy(blocks, 0, copy.blocks, 0, blocks.length);
        copy.counts.putAll(counts);
        copy.crystal = crystal;
        return copy;
    }

    private int index(int x, int y, int z) {
        if (x < 0 || x >= size || y < 0 || y >= size || z < 0 || z >= size) {
            throw new IndexOutOfBoundsException(
                    "(" + x + ", " + y + ", " + z + ") is outside a " + size + "³ fortress");
        }
        return (y * size + z) * size + x;
    }

    private void increment(String block) {
        if (!AIR.equals(block)) {
            counts.merge(block, 1, Integer::sum);
        }
    }

    private void decrement(String block) {
        if (AIR.equals(block)) {
            return;
        }
        counts.computeIfPresent(block, (key, count) -> count == 1 ? null : count - 1);
    }
}

package fr.ayoub.pvp.domain.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A photograph of a piece of the map, taken while it is still whole.
 *
 * <p><b>Why a photograph and not an undo log.</b> The engine used to remember every block a match
 * changed and put those back afterwards. An undo log can only undo <em>what it saw</em>: it lived
 * in memory, so a server that was killed rather than stopped lost it entirely, and any change that
 * arrived through an event nobody had hooked was invisible to it. Miss one, once, and the damage
 * is permanent and cumulative — which is how a tree cut down three matches earlier was still lying
 * on the ground.
 *
 * <p>A snapshot cannot have that bug, because it does not care what happened. It knows what the
 * map is <b>supposed to be</b>, and it puts that back. It is also the only approach that works on
 * a map the engine did not build: when a designer hands over an arena there is no generator to
 * re-run, and there will not need to be one.
 *
 * <p>It is a <b>palette plus one index per block</b>, because an arena is overwhelmingly the same
 * handful of blocks repeated a million times — a 128-block island is nine hundred thousand cells
 * and perhaps fifty distinct states. The full block <i>state</i> is kept, not the type: a
 * staircase facing the wrong way is a different arena.
 */
public final class VolumeSnapshot {

    public static final String AIR = "minecraft:air";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette;
    private final short[] cells;

    private VolumeSnapshot(int sizeX, int sizeY, int sizeZ, List<String> palette, short[] cells) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.cells = cells;
    }

    public static Builder builder(int sizeX, int sizeY, int sizeZ) {
        return new Builder(sizeX, sizeY, sizeZ);
    }

    /**
     * Straight from a palette and a cell array.
     *
     * <p>For the codec, and for a capture that has already built both — walking a million blocks
     * through {@link Builder#set} would intern the same string a million times, when the caller
     * already knows the palette is fifty entries long.
     */
    public static VolumeSnapshot of(int sizeX, int sizeY, int sizeZ,
                                    List<String> palette, short[] cells) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "a volume needs a size, got " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (cells.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("the cells do not fill the volume: "
                    + cells.length + " for " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("a snapshot with no palette describes nothing");
        }
        return new VolumeSnapshot(sizeX, sizeY, sizeZ, palette, cells);
    }

    public String at(int x, int y, int z) {
        return palette.get(Short.toUnsignedInt(cells[index(x, y, z, sizeX, sizeY, sizeZ)]));
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int blocks() {
        return cells.length;
    }

    public List<String> palette() {
        return palette;
    }

    public short[] cells() {
        return cells;
    }

    private static int index(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            throw new IndexOutOfBoundsException("outside the volume: " + x + "," + y + "," + z);
        }
        return (y * sizeZ + z) * sizeX + x;
    }

    /**
     * Fills a snapshot one block at a time, interning each state as it goes.
     *
     * <p>Air is index 0 and is put in first, so a volume nobody wrote a single block into is an
     * honest volume of air rather than a special case waiting to be forgotten.
     */
    public static final class Builder {

        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final short[] cells;

        private final List<String> palette = new ArrayList<>();
        private final Map<String, Short> index = new HashMap<>();

        private Builder(int sizeX, int sizeY, int sizeZ) {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException(
                        "a volume needs a size, got " + sizeX + "x" + sizeY + "x" + sizeZ);
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.cells = new short[sizeX * sizeY * sizeZ];

            intern(AIR);   // index 0, and therefore the default of an untouched cell
        }

        public Builder set(int x, int y, int z, String state) {
            cells[VolumeSnapshot.index(x, y, z, sizeX, sizeY, sizeZ)] = intern(state);
            return this;
        }

        private short intern(String state) {
            Short known = index.get(state);
            if (known != null) {
                return known;
            }
            if (palette.size() > Short.MAX_VALUE) {
                throw new IllegalStateException("more than " + Short.MAX_VALUE
                        + " distinct block states in one arena — that is not an arena");
            }
            short id = (short) palette.size();
            palette.add(state);
            index.put(state, id);
            return id;
        }

        public VolumeSnapshot build() {
            return new VolumeSnapshot(sizeX, sizeY, sizeZ, palette, cells);
        }
    }
}

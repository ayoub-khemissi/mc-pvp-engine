package fr.ayoub.pvp.domain.arena;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A snapshot, on disk.
 *
 * <p>It has to persist, and that is not a nicety. The point of a snapshot is that the arena is
 * restored to a known-good state <b>whatever happened</b> — and "the server was killed rather than
 * stopped" is exactly the case an in-memory copy cannot survive. That was the old undo log's fatal
 * flaw, and re-capturing the snapshot at every boot would inherit it: after a crash mid-match, the
 * engine would photograph a wrecked arena and then faithfully restore the wreckage forever.
 *
 * <p>So the file is written once, from a map that is known to be whole, and every boot afterwards
 * reads it. The magic number and version sit <b>outside</b> the gzip, so a file from an older
 * engine is rejected on its first four bytes instead of exploding somewhere in the middle.
 *
 * <p>It compresses to nothing, because an arena is enormous stretches of the same block: a quarter
 * of a million cells come to a few kilobytes.
 */
public final class VolumeSnapshotCodec {

    private static final byte[] MAGIC = {'A', 'R', 'N', 'A'};
    private static final byte VERSION = 1;

    private VolumeSnapshotCodec() {
    }

    public static byte[] encode(VolumeSnapshot snapshot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            bytes.write(MAGIC);
            bytes.write(VERSION);

            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeInt(snapshot.sizeX());
                out.writeInt(snapshot.sizeY());
                out.writeInt(snapshot.sizeZ());

                out.writeInt(snapshot.palette().size());
                for (String state : snapshot.palette()) {
                    out.writeUTF(state);
                }
                for (short cell : snapshot.cells()) {
                    out.writeShort(cell);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);   // a ByteArrayOutputStream cannot fail
        }
        return bytes.toByteArray();
    }

    public static VolumeSnapshot decode(byte[] bytes) {
        if (bytes.length < MAGIC.length + 1) {
            throw new IllegalArgumentException("not a snapshot: only " + bytes.length + " bytes");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                throw new IllegalArgumentException("not a snapshot: wrong magic number");
            }
        }
        if (bytes[MAGIC.length] != VERSION) {
            throw new IllegalArgumentException(
                    "snapshot version " + bytes[MAGIC.length] + ", this engine writes " + VERSION);
        }

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(bytes, MAGIC.length + 1, bytes.length - MAGIC.length - 1)))) {

            int sizeX = in.readInt();
            int sizeY = in.readInt();
            int sizeZ = in.readInt();

            int entries = in.readInt();
            List<String> palette = new ArrayList<>(entries);
            for (int i = 0; i < entries; i++) {
                palette.add(in.readUTF());
            }

            short[] cells = new short[sizeX * sizeY * sizeZ];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = in.readShort();
            }

            return VolumeSnapshot.of(sizeX, sizeY, sizeZ, palette, cells);

        } catch (IOException e) {
            throw new IllegalArgumentException("corrupt snapshot: " + e.getMessage(), e);
        }
    }
}

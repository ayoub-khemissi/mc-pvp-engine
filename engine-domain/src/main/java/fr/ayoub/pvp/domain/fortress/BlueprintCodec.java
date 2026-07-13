package fr.ayoub.pvp.domain.fortress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A fortress, to and from bytes — what actually goes in the database.
 *
 * A 20³ fortress is 8000 cells, and almost all of them are air. Stored naively that is
 * 8000 block names per save, per player, per slot. So: a <b>palette</b> of the distinct
 * blocks used, the cube as indices into it, and the whole thing gzipped. An empty fortress
 * comes out under a hundred bytes.
 *
 * <p>The header — magic and version — is left <b>outside</b> the compression on purpose:
 * a row written by a future version is recognised and refused, rather than decompressed
 * and misread into a fortress that looks plausible and is wrong.
 *
 * <p>The cube's size is written into the data. A fortress saved when the cube was 12 must
 * still load after the config says 20 — everything about Fortress is still being tuned,
 * and a config change must not silently corrupt what players have already built.
 */
public final class BlueprintCodec {

    private static final byte[] MAGIC = {'F', 'T', 'R', 'S'};
    private static final byte VERSION = 1;

    private BlueprintCodec() {
    }

    public static byte[] encode(Blueprint blueprint) {
        // The palette, in the order the blocks are first met — deterministic, so the same
        // fortress always encodes to the same bytes and an unchanged save is not a change.
        List<String> palette = new ArrayList<>();
        Map<String, Integer> indexOf = new HashMap<>();
        palette.add(Blueprint.AIR);
        indexOf.put(Blueprint.AIR, 0);

        int size = blueprint.size();
        int[] cells = new int[size * size * size];
        int cell = 0;

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    String block = blueprint.get(x, y, z);
                    Integer index = indexOf.get(block);
                    if (index == null) {
                        index = palette.size();
                        palette.add(block);
                        indexOf.put(block, index);
                    }
                    cells[cell++] = index;
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(MAGIC);
            out.write(VERSION);

            try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out))) {
                data.writeInt(size);

                data.writeInt(palette.size());
                for (String block : palette) {
                    data.writeUTF(block);
                }

                for (int index : cells) {
                    writeVarInt(data, index);
                }

                BlockPos crystal = blueprint.crystal();
                data.writeBoolean(crystal != null);
                if (crystal != null) {
                    data.writeInt(crystal.x());
                    data.writeInt(crystal.y());
                    data.writeInt(crystal.z());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not encode a fortress", e);
        }
        return out.toByteArray();
    }

    public static Blueprint decode(byte[] bytes) {
        if (bytes == null || bytes.length < MAGIC.length + 1) {
            throw new IllegalArgumentException("not a fortress: too short");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                throw new IllegalArgumentException("not a fortress: bad header");
            }
        }
        byte version = bytes[MAGIC.length];
        if (version != VERSION) {
            throw new IllegalArgumentException("this fortress was saved by a newer server "
                    + "(format " + version + ", this one reads " + VERSION + ")");
        }

        try (DataInputStream data = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(bytes, MAGIC.length + 1, bytes.length - MAGIC.length - 1)))) {

            int size = data.readInt();
            Blueprint blueprint = new Blueprint(size);

            int paletteSize = data.readInt();
            String[] palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = data.readUTF();
            }

            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        int index = readVarInt(data);
                        if (index != 0) {   // 0 is always AIR
                            blueprint.set(x, y, z, palette[index]);
                        }
                    }
                }
            }

            if (data.readBoolean()) {
                blueprint.crystal(new BlockPos(data.readInt(), data.readInt(), data.readInt()));
            }
            return blueprint;

        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("corrupt fortress data", e);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            byte read = in.readByte();
            value |= (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("varint too long");
            }
        }
    }
}

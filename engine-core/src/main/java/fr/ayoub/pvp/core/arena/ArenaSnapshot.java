package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.arena.VolumeSnapshot;
import fr.ayoub.pvp.domain.arena.VolumeSnapshotCodec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an arena is supposed to look like — and the machinery that makes it look like that again.
 *
 * <h2>Why this replaced the undo log</h2>
 *
 * The engine used to keep a journal: every block a match changed was remembered, and put back when
 * the match ended. An undo log can only undo <b>what it saw</b>. It lived in memory, so a server
 * that was killed rather than stopped lost it outright; it only knew about changes that arrived
 * through an event somebody had thought to hook; and every miss was <b>permanent and cumulative</b>,
 * because the next journal started from the damaged map and called it the baseline. That is how a
 * tree cut down three matches earlier was still lying on the ground.
 *
 * <p>A snapshot has no such failure mode, because it does not care what happened. It knows what the
 * arena is supposed to be and it puts that back — every block compared, every time. It is also the
 * only thing that can work on a map the engine did not build: a designer's arena has no generator
 * to re-run, and now it does not need one. The engine photographs it and stops asking questions.
 *
 * <h2>What is in the photograph</h2>
 *
 * The blocks, as full states (a staircase facing the wrong way is a different arena) — and the
 * <b>contents of every container</b>, because a snapshot that restores a chest and leaves it empty
 * has not restored the map, it has restored a map where the loot has already been taken.
 *
 * <p>It is written to disk, and that is not a nicety: re-photographing at every boot would inherit
 * the very flaw it exists to fix. After a crash mid-match the engine would photograph the wreckage
 * and then faithfully restore <em>the wreckage</em>, for ever.
 */
public final class ArenaSnapshot {

    private static final byte[] MAGIC = {'A', 'R', 'E', 'N'};
    private static final byte VERSION = 1;

    /** One box of the arena: what it holds, and what is inside its chests. */
    private record Part(Volume volume, VolumeSnapshot blocks, Map<Long, byte[]> containers) {
    }

    private final List<Part> parts;

    private ArenaSnapshot(List<Part> parts) {
        this.parts = parts;
    }

    // --- taking it ------------------------------------------------------------------------

    /**
     * Photograph the arena as it stands. It had better be whole.
     *
     * <p>The palette is built from {@link BlockData} rather than from its string form. Paper hands
     * out the same immutable instance for the same state, so this is a reference comparison a
     * million times over instead of a million strings allocated and thrown away — and the strings
     * are only produced for the fifty entries that actually end up in the palette.
     */
    public static ArenaSnapshot capture(World world, List<Volume> volumes) {
        List<Part> parts = new ArrayList<>();

        for (Volume volume : volumes) {
            List<BlockData> palette = new ArrayList<>();
            Map<BlockData, Short> index = new HashMap<>();
            short[] cells = new short[(int) volume.blocks()];

            Map<Long, byte[]> containers = new HashMap<>();

            int cell = 0;
            for (int y = volume.minY(); y <= volume.maxY(); y++) {
                for (int z = volume.minZ(); z <= volume.maxZ(); z++) {
                    for (int x = volume.minX(); x <= volume.maxX(); x++) {
                        Block block = world.getBlockAt(x, y, z);
                        BlockData data = block.getBlockData();

                        Short known = index.get(data);
                        if (known == null) {
                            known = (short) palette.size();
                            palette.add(data);
                            index.put(data, known);
                        }
                        cells[cell++] = known;

                        // A chest the map put loot in. Restoring the block and not the loot would
                        // hand every match after the first one an arena that had been looted.
                        BlockState state = block.getState();
                        if (state instanceof Container container) {
                            containers.put(key(volume, x, y, z), ItemStack.serializeItemsAsBytes(
                                    container.getInventory().getContents()));
                        }
                    }
                }
            }

            List<String> states = palette.stream().map(BlockData::getAsString).toList();

            parts.add(new Part(volume,
                    VolumeSnapshot.of(volume.sizeX(), volume.sizeY(), volume.sizeZ(), states, cells),
                    containers));
        }
        return new ArenaSnapshot(parts);
    }

    public long blocks() {
        return parts.stream().mapToLong(part -> part.volume().blocks()).sum();
    }

    // --- putting it back ------------------------------------------------------------------

    /**
     * Make the arena match the photograph again, a budget of blocks per tick.
     *
     * <p>Only the blocks that are <b>wrong</b> are written. A match that dug three holes costs three
     * holes' worth of writes; the rest of the volume is a comparison and nothing more. That is what
     * makes it affordable to check the whole thing rather than trust a list of what we think we
     * changed — and checking the whole thing is the entire point.
     *
     * <p>No physics, deliberately. A torch is put back before the wall it hangs on as often as
     * after it, and with physics on it would pop straight back off onto the floor.
     */
    public void reset(Plugin plugin, World world, int budget, Runnable done) {
        sweep(world);
        new ResetJob(plugin, world, Math.max(1, budget), done).start();
    }

    /** Walks the whole arena, a slice a tick, and writes only what is wrong. */
    private final class ResetJob implements Runnable {

        private final Plugin plugin;
        private final World world;
        private final int budget;
        private final Runnable done;

        /** The palette, resolved once. Paper interns BlockData, so these are cheap to compare. */
        private final List<BlockData[]> palettes = new ArrayList<>();

        private BukkitTask task;
        private int part;
        private int cell;
        private int fixed;

        ResetJob(Plugin plugin, World world, int budget, Runnable done) {
            this.plugin = plugin;
            this.world = world;
            this.budget = budget;
            this.done = done;

            for (Part each : parts) {
                palettes.add(each.blocks().palette().stream()
                        .map(Bukkit::createBlockData)
                        .toArray(BlockData[]::new));
            }
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        }

        @Override
        public void run() {
            int left = budget;

            while (left > 0 && part < parts.size()) {
                Part current = parts.get(part);
                VolumeSnapshot blocks = current.blocks();
                Volume volume = current.volume();

                if (cell >= blocks.blocks()) {
                    restoreContainers(world, current);
                    part++;
                    cell = 0;
                    continue;
                }

                int at = cell++;
                left--;

                int x = at % volume.sizeX();
                int z = (at / volume.sizeX()) % volume.sizeZ();
                int y = at / (volume.sizeX() * volume.sizeZ());

                BlockData want = palettes.get(part)[Short.toUnsignedInt(blocks.cells()[at])];
                Block block = world.getBlockAt(
                        volume.minX() + x, volume.minY() + y, volume.minZ() + z);

                if (!block.getBlockData().equals(want)) {
                    block.setBlockData(want, false);   // no physics: see the method comment
                    fixed++;
                }
            }

            if (part < parts.size()) {
                return;
            }

            task.cancel();
            if (fixed > 0) {
                plugin.getLogger().info("Arena reset: " + fixed + " block(s) put back.");
            }
            done.run();
        }
    }

    /** Every chest back to the loot the map put in it. */
    private static void restoreContainers(World world, Part part) {
        Volume volume = part.volume();

        for (Map.Entry<Long, byte[]> entry : part.containers().entrySet()) {
            long packed = entry.getKey();

            int x = volume.minX() + (int) (packed >> 40 & 0xFFFFF);
            int y = volume.minY() + (int) (packed >> 20 & 0xFFFFF);
            int z = volume.minZ() + (int) (packed & 0xFFFFF);

            if (world.getBlockAt(x, y, z).getState() instanceof Container container) {
                container.getInventory().setContents(
                        ItemStack.deserializeItemsFromBytes(entry.getValue()));
                container.update(true, false);
            }
        }
    }

    /**
     * Everything the match left lying around.
     *
     * <p>Items, arrows, mobs, crystals, the body of somebody who logged out — none of it belongs to
     * the next match, and a mode that wants its mobs back puts them back itself. Only what is
     * <b>inside this arena's boxes</b> is touched: another match may be running on the island next
     * door, and it is not ours to clear.
     *
     * <p>Players are left alone for the obvious reason. So are paintings, item frames and armour
     * stands: a designer may have <b>placed</b> those, the block snapshot cannot put an entity back,
     * and removing them would be a one-way door.
     */
    private void sweep(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player || entity instanceof Hanging || entity instanceof ArmorStand) {
                continue;
            }
            if (parts.stream().anyMatch(part -> part.volume().contains(entity.getLocation()))) {
                entity.remove();
            }
        }
    }

    // --- the file -------------------------------------------------------------------------

    public void save(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(MAGIC);
        bytes.write(VERSION);

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(parts.size());

            for (Part part : parts) {
                Volume volume = part.volume();
                out.writeInt(volume.minX());
                out.writeInt(volume.minY());
                out.writeInt(volume.minZ());
                out.writeInt(volume.maxX());
                out.writeInt(volume.maxY());
                out.writeInt(volume.maxZ());

                byte[] encoded = VolumeSnapshotCodec.encode(part.blocks());
                out.writeInt(encoded.length);
                out.write(encoded);

                out.writeInt(part.containers().size());
                for (Map.Entry<Long, byte[]> entry : part.containers().entrySet()) {
                    out.writeLong(entry.getKey());
                    out.writeInt(entry.getValue().length);
                    out.write(entry.getValue());
                }
            }
        }

        file.getParentFile().mkdirs();
        Files.write(file.toPath(), bytes.toByteArray());
    }

    public static ArenaSnapshot load(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());

        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes.length <= i || bytes[i] != MAGIC[i]) {
                throw new IOException("not an arena snapshot: " + file.getName());
            }
        }
        if (bytes[MAGIC.length] != VERSION) {
            throw new IOException("snapshot version " + bytes[MAGIC.length]
                    + ", this engine reads " + VERSION);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                bytes, MAGIC.length + 1, bytes.length - MAGIC.length - 1))) {

            int count = in.readInt();
            List<Part> parts = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                Volume volume = new Volume(in.readInt(), in.readInt(), in.readInt(),
                        in.readInt(), in.readInt(), in.readInt());

                byte[] encoded = new byte[in.readInt()];
                in.readFully(encoded);

                int containerCount = in.readInt();
                Map<Long, byte[]> containers = new HashMap<>(containerCount);
                for (int c = 0; c < containerCount; c++) {
                    long at = in.readLong();
                    byte[] items = new byte[in.readInt()];
                    in.readFully(items);
                    containers.put(at, items);
                }

                parts.add(new Part(volume, VolumeSnapshotCodec.decode(encoded), containers));
            }
            return new ArenaSnapshot(parts);
        }
    }

    /** A position inside a volume, packed. 20 bits an axis — no arena is a million blocks wide. */
    private static long key(Volume volume, int x, int y, int z) {
        return (long) (x - volume.minX()) << 40
                | (long) (y - volume.minY()) << 20
                | (z - volume.minZ());
    }
}

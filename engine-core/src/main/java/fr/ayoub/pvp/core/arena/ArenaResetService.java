package fr.ayoub.pvp.core.arena;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps every arena looking the way it is supposed to look.
 *
 * <h2>The promise</h2>
 *
 * <b>Every match starts on a whole map.</b> Not "a map we undid our own changes to" — a whole map.
 * The difference is the difference between a system that works and one that quietly rots: the old
 * journal remembered what it had seen a match do and put that back, so a single missed event, or a
 * server killed rather than stopped, left damage that was never repaired and became the baseline
 * for everything after it. That is why a tree cut down three matches ago was still on the ground.
 *
 * <h2>How</h2>
 *
 * <ol>
 *   <li><b>Once</b>, on a map that is whole, the arena is photographed and the photograph is written
 *       to disk. Blocks, and the contents of every chest.
 *   <li><b>At boot</b>, every arena is put back to its photograph. This is what makes a crash
 *       survivable: a server that died mid-match wakes up on a clean map, not on the wreckage.
 *   <li><b>After every match</b>, the same. The arena stays busy until the last block is back, so
 *       it is never handed to the next match as a building site.
 * </ol>
 *
 * <p>The photograph is taken from the world, not from a generator — which is the only reason this
 * will work when the maps are made by a designer instead of by {@code FortressMapBuilder}. The
 * engine does not need to know how an arena was built. It only needs to know what it looked like.
 */
public final class ArenaResetService {

    private final Plugin plugin;
    private final File folder;
    private final int budget;

    private final Map<String, ArenaSnapshot> snapshots = new HashMap<>();

    public ArenaResetService(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "snapshots");
        this.budget = Math.max(1, plugin.getConfig().getInt("match.reset-blocks-per-tick", 20000));
    }

    /**
     * Make sure every arena has a photograph, and is currently equal to it.
     *
     * <p>Called once, at boot, when nothing is running. An arena with no photograph is photographed
     * <b>as it is</b> — so this had better be a map nobody has played on yet, which on a fresh
     * server or a freshly generated map it is. An arena that already has one is reset to it, which
     * is what repairs a map the server was killed in the middle of.
     */
    public void prepare(Iterable<Arena> arenas) {
        for (Arena arena : arenas) {
            if (arena.reset().isEmpty()) {
                continue;   // the map never said what may be wrecked. Nothing to put back.
            }

            File file = new File(folder, arena.id() + ".snapshot");

            if (file.exists()) {
                load(arena, file);
            } else {
                take(arena, file);
            }
        }
    }

    private void load(Arena arena, File file) {
        try {
            ArenaSnapshot snapshot = ArenaSnapshot.load(file);
            snapshots.put(arena.id(), snapshot);

            // Put it back NOW, before anybody can queue. If the server was killed in the middle of
            // a match, this is the moment the damage is undone — and it is the only moment, because
            // nothing else in the engine ever gets to see a map it did not itself hand out.
            snapshot.reset(plugin, arena.world(), budget, () -> { });

        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Could not read the snapshot of '" + arena.id() + "': "
                    + e.getMessage() + " — this arena will NOT be reset between matches."
                    + " Delete plugins/PvPEngine/snapshots/" + arena.id()
                    + ".snapshot to have it taken again from the map as it stands.");
        }
    }

    private void take(Arena arena, File file) {
        long started = System.currentTimeMillis();

        ArenaSnapshot snapshot = ArenaSnapshot.capture(arena.world(), arena.reset());
        snapshots.put(arena.id(), snapshot);

        try {
            snapshot.save(file);
            plugin.getLogger().info("Photographed '" + arena.id() + "': " + snapshot.blocks()
                    + " blocks in " + (System.currentTimeMillis() - started) + " ms.");

        } catch (IOException e) {
            plugin.getLogger().severe("Could not write the snapshot of '" + arena.id() + "': "
                    + e.getMessage() + " — it will be taken again on every boot, which means a"
                    + " server that crashes mid-match will photograph the wreckage.");
        }
    }

    /**
     * Put this arena back, and call {@code done} when the last block is in place.
     *
     * <p>The caller must not release the arena until then. Released early, the next match would be
     * handed a building site — with the reset still running through it.
     */
    public void reset(Arena arena, Runnable done) {
        ArenaSnapshot snapshot = snapshots.get(arena.id());

        if (snapshot == null) {
            done.run();   // no photograph: nothing to put back, and nothing to wait for
            return;
        }
        snapshot.reset(plugin, arena.world(), budget, done);
    }

    /** Does this arena get put back between matches? */
    public boolean covers(Arena arena) {
        return snapshots.containsKey(arena.id());
    }
}

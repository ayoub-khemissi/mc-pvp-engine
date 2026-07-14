package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.arena.BlockKey;
import fr.ayoub.pvp.domain.arena.RestorePlan;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a match changed, so the map can be put back.
 *
 * A destructible arena is only destructible <b>once</b> unless somebody undoes it. The second
 * match on a dug-out island would be played in the ruins of the first, with the ore already
 * mined and a hole where the cave used to be.
 *
 * <p>The obvious approach — photograph the whole arena at the start — is a million blocks a
 * match. So instead: <b>remember a block the first time it is about to change, and never
 * again</b>. A player who mines the same seam for ten minutes still costs one entry, and a
 * match where nobody digs costs nothing at all.
 *
 * <h2>Two things here are about scale, not correctness</h2>
 *
 * <p><b>What is stored.</b> Almost every block is fully described by its {@link BlockData} —
 * and Paper hands out the <b>same immutable instance</b> for the same state, so a hundred
 * thousand stone blocks cost a hundred thousand references to one object. Only a
 * {@link TileState} (a chest, a sign, a furnace) needs the heavyweight {@link BlockState},
 * because only it carries something the block type cannot say: what was inside it, what it
 * said. Keeping a BlockState for <em>every</em> block — which is what this used to do — cost
 * roughly ten times the memory for nothing.
 *
 * <p><b>When it is put back.</b> Not all at once. A long Fortress match moves blocks by the
 * hundred thousand, and undoing that inside a single tick freezes the whole server — every
 * other match with it. {@link #restore} spends a budget per tick instead. Nobody is waiting:
 * the match is over and its players are already in the lobby. The arena is simply not handed
 * out again until the last block is back, which is what the {@code done} callback is for.
 */
public final class ArenaJournal {

    private final World world;

    /** The cheap case, and 99% of them: a reference to an immutable, shared BlockData. */
    private final Map<Long, BlockData> before = new HashMap<>();

    /** The expensive case: chests keep their contents, signs their text. */
    private final Map<Long, BlockState> tiles = new HashMap<>();

    private BukkitTask task;

    public ArenaJournal(World world) {
        this.world = world;
    }

    /**
     * This block is about to change. Remember what it was — <b>if we do not already know</b>.
     *
     * The "already know" is the whole trick. The first write is the one that matters: after
     * that the block is ours, and whatever it becomes is what we are going to undo.
     */
    public void remember(Block block) {
        long key = BlockKey.of(block.getX(), block.getY(), block.getZ());
        if (known(key)) {
            return;
        }

        BlockState state = block.getState();
        if (state instanceof TileState) {
            tiles.put(key, state);
        } else {
            before.put(key, block.getBlockData());
        }
    }

    /**
     * Same, from a state somebody already captured — a chest about to be opened, a block an
     * event handed us as a state rather than a block.
     */
    public void remember(BlockState state) {
        long key = BlockKey.of(state.getX(), state.getY(), state.getZ());
        if (known(key)) {
            return;
        }

        if (state instanceof TileState) {
            tiles.put(key, state);
        } else {
            before.put(key, state.getBlockData());
        }
    }

    private boolean known(long key) {
        return before.containsKey(key) || tiles.containsKey(key);
    }

    /** How many blocks this match changed. */
    public int size() {
        return before.size() + tiles.size();
    }

    /**
     * Put it all back, a budget of blocks per tick, and call {@code done} when the arena is
     * whole again. Returns immediately: the caller must not release the arena until the
     * callback fires, or the next match would be handed a building site.
     */
    public void restore(Plugin plugin, int blocksPerTick, Runnable done) {
        if (size() == 0) {
            done.run();
            return;
        }

        RestorePlan plan = new RestorePlan(new ArrayList<>(keys()), Math.max(1, blocksPerTick));

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plan.isDone()) {
                finish(done);
                return;
            }
            apply(plan.next());

            if (plan.isDone()) {
                finish(done);
            }
        }, 1L, 1L);
    }

    /**
     * Put it all back <b>now</b>, in this tick, and never mind the spike.
     *
     * This is the shutdown path. There is no next tick to spread the work over, and a server
     * that stops with an arena half-cleared writes the ruins to disk.
     */
    public int restoreNow() {
        int blocks = size();
        if (blocks == 0) {
            return 0;
        }

        RestorePlan plan = new RestorePlan(new ArrayList<>(keys()), Integer.MAX_VALUE);
        while (!plan.isDone()) {
            apply(plan.next());
        }

        clear();
        return blocks;
    }

    private void apply(RestorePlan.Batch batch) {
        for (long key : batch.keys()) {
            Block block = world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key));

            if (batch.step() == RestorePlan.Step.CLEAR) {
                block.setType(Material.AIR, false);
                continue;
            }

            BlockData data = before.get(key);
            if (data != null) {
                block.setBlockData(data, false);   // no physics: its support is already back
            } else {
                tiles.get(key).update(true, false);
            }
        }
    }

    private List<Long> keys() {
        List<Long> keys = new ArrayList<>(size());
        keys.addAll(before.keySet());
        keys.addAll(tiles.keySet());
        return keys;
    }

    private void finish(Runnable done) {
        cancel();
        clear();
        done.run();
    }

    /** The match died under us, or the server is going down mid-restore. */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void clear() {
        before.clear();
        tiles.clear();
    }
}

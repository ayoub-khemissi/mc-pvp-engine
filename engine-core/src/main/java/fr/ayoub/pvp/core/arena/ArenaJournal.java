package fr.ayoub.pvp.core.arena;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a match changed, so the map can be put back.
 *
 * A destructible arena is only destructible <b>once</b> unless somebody undoes it. The
 * second match on a dug-out island would be played in the ruins of the first, with the ore
 * already mined and a hole where the cave used to be.
 *
 * <p>The obvious approach — photograph the whole arena at the start — is a million blocks a
 * match. So instead: <b>remember a block the first time it is about to change, and never
 * again</b>. A player who mines the same seam for ten minutes still costs us one entry per
 * block, and a match where nobody digs costs nothing at all.
 *
 * <p>What is stored is the {@link BlockState}, not the block type: a chest goes back with
 * what was in it, a sign with what it said. Restoring the type alone would give the next
 * match an island of empty chests.
 */
public final class ArenaJournal {

    private final Map<Long, BlockState> before = new HashMap<>();

    /**
     * This block is about to change. Remember what it was — <b>if we do not already know</b>.
     *
     * The "already know" is the whole trick. The first write is the one that matters: after
     * that the block is ours, and whatever it becomes is what we are going to undo.
     */
    public void remember(Block block) {
        before.putIfAbsent(key(block), block.getState());
    }

    public void remember(BlockState state) {
        before.putIfAbsent(key(state), state);
    }

    public int size() {
        return before.size();
    }

    /**
     * Put it all back.
     *
     * Two passes, and the order is not cosmetic. Everything is set to plain air first,
     * because a restore that runs in map order will happily place a torch before the wall it
     * hangs on and watch it pop off onto the floor. With the region emptied, the second pass
     * can put everything back knowing its support is already there.
     */
    public int restore() {
        List<BlockState> states = new ArrayList<>(before.values());

        for (BlockState state : states) {
            state.getBlock().setType(org.bukkit.Material.AIR, false);
        }
        for (BlockState state : states) {
            state.update(true, false);   // force, and do not trigger physics
        }

        int restored = states.size();
        before.clear();
        return restored;
    }

    private static long key(Block block) {
        return key(block.getX(), block.getY(), block.getZ());
    }

    private static long key(BlockState state) {
        return key(state.getX(), state.getY(), state.getZ());
    }

    /** Three coordinates in one long, so the map holds no objects it does not need. */
    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }
}

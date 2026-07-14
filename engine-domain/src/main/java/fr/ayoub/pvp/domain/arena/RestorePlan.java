package fr.ayoub.pvp.domain.arena;

import java.util.List;

/**
 * The order in which a dug-out arena is put back, and the rate.
 *
 * <p><b>The order.</b> Two passes: every block is cleared to air, and only then is every block
 * put back. Rebuilding in map order places a torch before the wall it hangs on, and the torch
 * pops off onto the floor. With the region emptied first, nothing has to wait for its support.
 *
 * <p><b>The rate.</b> A thirty-minute Fortress match can move a hundred thousand blocks. Doing
 * that in one tick freezes the <b>whole server</b> — every other match with it — for a second
 * or more. So the work comes out in batches, and the caller spends one batch a tick. Nobody is
 * waiting on the arena: the match is already over and the players are already in the lobby.
 */
public final class RestorePlan {

    public enum Step {
        /** Set the block to air. Physics off. */
        CLEAR,
        /** Put back what was there. */
        REBUILD
    }

    /** One tick's worth of work. */
    public record Batch(Step step, List<Long> keys) {
    }

    private final List<Long> keys;
    private final int budget;

    private Step step = Step.CLEAR;
    private int cursor;

    /**
     * @param keys   the blocks the match changed, as {@link BlockKey} values
     * @param budget how many blocks may be touched in one tick
     */
    public RestorePlan(List<Long> keys, int budget) {
        if (budget <= 0) {
            throw new IllegalArgumentException("budget must be positive, was " + budget);
        }
        this.keys = List.copyOf(keys);
        this.budget = budget;
    }

    /** The number of blocks the match changed. */
    public int blocks() {
        return keys.size();
    }

    /** Blocks still to touch, both passes counted. */
    public int remaining() {
        int left = keys.size() - cursor;
        return step == Step.CLEAR ? left + keys.size() : left;
    }

    public boolean isDone() {
        return remaining() == 0;
    }

    /** The next tick's work. */
    public Batch next() {
        if (isDone()) {
            throw new IllegalStateException("the restore is already finished");
        }

        int to = Math.min(cursor + budget, keys.size());
        Batch batch = new Batch(step, keys.subList(cursor, to));
        cursor = to;

        if (cursor == keys.size() && step == Step.CLEAR) {
            step = Step.REBUILD;
            cursor = 0;
        }
        return batch;
    }
}

package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting a dug-out arena back, a slice at a time.
 *
 * A thirty-minute Fortress match can move a hundred thousand blocks. Undoing that in one tick
 * is a one-to-three second freeze of the <b>whole server</b> — every other match included. So
 * the restore is handed out in batches, and the plan is what decides the order and the size.
 *
 * <p>The order is not negotiable: <b>every</b> block is cleared to air before <b>any</b> block
 * is put back. Rebuilding in map order places a torch before the wall it hangs on and watches
 * it pop off onto the floor.
 */
class RestorePlanTest {

    private static List<Long> keys(int count) {
        List<Long> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(BlockKey.of(i, 64, 0));
        }
        return keys;
    }

    @Test
    void aMatchThatChangedNothingIsAlreadyDone() {
        RestorePlan plan = new RestorePlan(List.of(), 100);

        assertTrue(plan.isDone());
        assertEquals(0, plan.blocks());
    }

    @Test
    void clearsThenRebuilds() {
        RestorePlan plan = new RestorePlan(keys(1), 100);

        RestorePlan.Batch first = plan.next();
        assertEquals(RestorePlan.Step.CLEAR, first.step());
        assertEquals(keys(1), first.keys());
        assertFalse(plan.isDone());

        RestorePlan.Batch second = plan.next();
        assertEquals(RestorePlan.Step.REBUILD, second.step());
        assertEquals(keys(1), second.keys());
        assertTrue(plan.isDone());
    }

    /** THE rule. Nothing goes back until everything is gone. */
    @Test
    void neverRebuildsWhileAnythingIsStillStanding() {
        RestorePlan plan = new RestorePlan(keys(10), 3);

        boolean rebuilding = false;
        int cleared = 0;

        while (!plan.isDone()) {
            RestorePlan.Batch batch = plan.next();
            if (batch.step() == RestorePlan.Step.REBUILD) {
                rebuilding = true;
            } else {
                assertFalse(rebuilding, "cleared a block after starting to rebuild");
                cleared += batch.keys().size();
            }
        }

        assertEquals(10, cleared);
    }

    @Test
    void neverHandsOutMoreThanTheBudget() {
        RestorePlan plan = new RestorePlan(keys(10), 3);

        List<Long> cleared = new ArrayList<>();
        List<Long> rebuilt = new ArrayList<>();

        while (!plan.isDone()) {
            RestorePlan.Batch batch = plan.next();
            assertTrue(batch.keys().size() <= 3, "batch of " + batch.keys().size());
            (batch.step() == RestorePlan.Step.CLEAR ? cleared : rebuilt).addAll(batch.keys());
        }

        // Every block, exactly once, in each pass.
        assertEquals(keys(10), cleared);
        assertEquals(keys(10), rebuilt);
    }

    @Test
    void countsWhatIsLeft() {
        RestorePlan plan = new RestorePlan(keys(4), 4);

        assertEquals(8, plan.remaining());   // four to clear, four to put back
        plan.next();
        assertEquals(4, plan.remaining());
        plan.next();
        assertEquals(0, plan.remaining());
    }

    @Test
    void refusesAnEmptyBudget() {
        assertThrows(IllegalArgumentException.class, () -> new RestorePlan(keys(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new RestorePlan(keys(1), -1));
    }

    @Test
    void refusesToRunPastTheEnd() {
        RestorePlan plan = new RestorePlan(keys(1), 10);
        plan.next();
        plan.next();

        assertThrows(IllegalStateException.class, plan::next);
    }
}

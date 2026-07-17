package fr.ayoub.pvp.domain.arena;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Do the two teams actually face each other?
 *
 * <p>A duel arena is broken in a quiet, maddening way if the spawns look the wrong way: one team
 * stares at a wall while the other runs up behind them. So before a map is saved, this checks the
 * one thing a human eye misses on a big build — that every spawn on a team faces the same straight
 * direction, and that the two teams face <b>opposite</b> ways (0 against 180, 90 against 270).
 *
 * <p>The yaws handed in are already snapped to cardinals ({@link CardinalFacing}); this only judges
 * whether they agree.
 */
class SpawnCoherenceTest {

    @Test
    void acceptsTwoTeamsFacingEachOther() {
        // Team 0 all facing south (0), team 1 all facing north (180).
        assertTrue(SpawnCoherence.check(List.of(0f, 0f, 0f), List.of(180f, 180f)).isEmpty());
    }

    @Test
    void acceptsTheOtherAxisToo() {
        assertTrue(SpawnCoherence.check(List.of(90f), List.of(270f)).isEmpty());
    }

    @Test
    void flagsATeamThatDoesNotAgreeWithItself() {
        List<String> problems = SpawnCoherence.check(List.of(0f, 90f), List.of(180f));
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.toLowerCase().contains("team 0")));
    }

    @Test
    void flagsTeamsThatDoNotFaceEachOther() {
        // Both facing south — they are looking the same way, not at each other.
        List<String> problems = SpawnCoherence.check(List.of(0f), List.of(0f));
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.toLowerCase().contains("face")));
    }

    @Test
    void flagsTeamsAtNinetyDegrees() {
        // 0 vs 90 is not opposite — they'd be looking across each other.
        assertFalse(SpawnCoherence.check(List.of(0f), List.of(90f)).isEmpty());
    }

    @Test
    void saysNothingUsefulWhenATeamHasNoSpawns() {
        // Nothing to compare yet — that is the draft's "missing" job, not coherence's.
        assertTrue(SpawnCoherence.check(List.of(), List.of(180f)).isEmpty());
        assertTrue(SpawnCoherence.check(List.of(0f), List.of()).isEmpty());
    }
}

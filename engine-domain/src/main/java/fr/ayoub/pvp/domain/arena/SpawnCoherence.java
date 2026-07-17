package fr.ayoub.pvp.domain.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the two teams of an arena actually face each other.
 *
 * <p>A map is broken in a quiet way when the spawns look wrong — one team facing a wall while the
 * other spawns behind them. A human eye misses it on a big build, so this checks it before the map
 * is saved: every spawn on a team faces the same straight direction, and the two teams face
 * <b>opposite</b> ways.
 *
 * <p>Not a hard failure by itself — the caller decides whether to warn or refuse. The yaws are
 * already snapped to cardinals ({@link CardinalFacing}); this only judges agreement.
 */
public final class SpawnCoherence {

    private SpawnCoherence() {
    }

    /**
     * @return the problems found, in words; empty when the two teams face each other cleanly. A team
     *         with no spawns yet is skipped — that is the draft's "still missing" job, not this one.
     */
    public static List<String> check(List<Float> team0Yaws, List<Float> team1Yaws) {
        List<String> problems = new ArrayList<>();

        if (team0Yaws.isEmpty() || team1Yaws.isEmpty()) {
            return problems;
        }

        Float facing0 = uniformFacing(team0Yaws);
        Float facing1 = uniformFacing(team1Yaws);

        if (facing0 == null) {
            problems.add("team 0's spawns do not all face the same way");
        }
        if (facing1 == null) {
            problems.add("team 1's spawns do not all face the same way");
        }
        if (facing0 != null && facing1 != null
                && facing1.floatValue() != CardinalFacing.opposite(facing0)) {
            problems.add("the two teams do not face each other "
                    + "(team 0 at " + facing0.intValue() + "°, team 1 at " + facing1.intValue() + "°)");
        }
        return problems;
    }

    /** The one direction a team faces, or null if its spawns disagree. */
    private static Float uniformFacing(List<Float> yaws) {
        float first = yaws.get(0);
        return yaws.stream().allMatch(yaw -> yaw.floatValue() == first) ? first : null;
    }
}

package fr.ayoub.pvp.domain.br;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each team finished, derived from the order they were eliminated.
 *
 * <p>Nobody is told their placement — it falls out of who outlived whom. The first team out of a
 * field of {@code teams} came last; the team out just before the end came 2nd; whoever is still
 * standing came 1st. So the board records only the elimination order, and every placement is a
 * subtraction from it. It is what the placement Elo and the HUD's "top N" both read.
 *
 * <p>Not Bukkit-aware and not tied to players — it deals in team indices, so it works for solo
 * (each team is one player) and for squads (a team is wiped when its last member falls) alike.
 */
public final class PlacementBoard {

    private final int teams;
    private final List<Integer> eliminationOrder = new ArrayList<>();

    public PlacementBoard(int teams) {
        if (teams < 2) {
            throw new IllegalArgumentException("a placement board needs at least 2 teams, got " + teams);
        }
        this.teams = teams;
    }

    /** This team is out. Idempotent: a team that is already out does not move. */
    public void eliminate(int teamIndex) {
        check(teamIndex);
        if (!eliminationOrder.contains(teamIndex)) {
            eliminationOrder.add(teamIndex);
        }
    }

    /**
     * Where this team finished, 1 being the winner.
     *
     * <p>An eliminated team at position {@code i} in the order (0 = out first) placed
     * {@code teams - i}. A team still standing placed {@code teams - eliminatedCount} — so the lone
     * survivor is 1st, and if a match ends with several still alive they tie for the best spot left.
     */
    public int placementOf(int teamIndex) {
        check(teamIndex);
        int i = eliminationOrder.indexOf(teamIndex);
        return i >= 0 ? teams - i : teams - eliminationOrder.size();
    }

    /** How many teams have not been eliminated. */
    public int remaining() {
        return teams - eliminationOrder.size();
    }

    private void check(int teamIndex) {
        if (teamIndex < 0 || teamIndex >= teams) {
            throw new IndexOutOfBoundsException("no team " + teamIndex + " in a field of " + teams);
        }
    }
}

package fr.ayoub.pvp.domain.match;

import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The score of a best-of-N match: who has won how many rounds, and whether that is
 * already enough.
 *
 * A series is <b>first to {@code bestOf / 2 + 1}</b>, so a 2-0 in a best-of-3 ends there —
 * a dead rubber is never played. The best-of must be odd: two rounds can end 1-1, and a
 * ranked ladder cannot have matches that produce no winner.
 *
 * Pure — the engine only reads it to decide whether to start another round.
 */
public final class Series {

    private final int bestOf;
    private final int[] wins;

    public Series(int bestOf, int teams) {
        if (bestOf < 1 || bestOf % 2 == 0) {
            throw new IllegalArgumentException("best-of must be odd (1, 3, 5…), got " + bestOf);
        }
        if (teams < 2) {
            throw new IllegalArgumentException("a series needs at least 2 teams, got " + teams);
        }
        this.bestOf = bestOf;
        this.wins = new int[teams];
    }

    public int bestOf() {
        return bestOf;
    }

    /** How many rounds a team must win to take the match. */
    public int roundsToWin() {
        return bestOf / 2 + 1;
    }

    public int wins(int team) {
        return wins[checked(team)];
    }

    public int roundsPlayed() {
        return IntStream.of(wins).sum();
    }

    /** 1-based: the round about to be played. */
    public int round() {
        return roundsPlayed() + 1;
    }

    public void recordRound(int team) {
        if (isDecided()) {
            throw new IllegalStateException("the series is already over");
        }
        wins[checked(team)]++;
    }

    public boolean isDecided() {
        return winner().isPresent();
    }

    public OptionalInt winner() {
        for (int team = 0; team < wins.length; team++) {
            if (wins[team] >= roundsToWin()) {
                return OptionalInt.of(team);
            }
        }
        return OptionalInt.empty();
    }

    /** One more round win and this team takes the match. */
    public boolean isMatchPoint(int team) {
        return wins[checked(team)] == roundsToWin() - 1;
    }

    /** "2 - 1", for the titles and the sidebar. */
    public String scoreline() {
        return IntStream.of(wins)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(" - "));
    }

    private int checked(int team) {
        if (team < 0 || team >= wins.length) {
            throw new IllegalArgumentException("no such team: " + team);
        }
        return team;
    }
}

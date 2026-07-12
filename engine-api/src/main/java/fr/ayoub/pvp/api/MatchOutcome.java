package fr.ayoub.pvp.api;

/** How a match ended. */
public record MatchOutcome(int winningTeam, Reason reason) {

    public enum Reason {
        LAST_TEAM_STANDING,
        TIME_LIMIT,
        FORFEIT,
        ABORTED
    }

    /** No winner (aborted, or a draw on time). */
    public static final int NO_TEAM = -1;

    public static MatchOutcome win(int team, Reason reason) {
        return new MatchOutcome(team, reason);
    }

    public static MatchOutcome aborted() {
        return new MatchOutcome(NO_TEAM, Reason.ABORTED);
    }

    public boolean hasWinner() {
        return winningTeam != NO_TEAM;
    }
}

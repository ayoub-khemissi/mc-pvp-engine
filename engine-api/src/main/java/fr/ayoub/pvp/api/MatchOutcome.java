package fr.ayoub.pvp.api;

/** How a match ended. */
public record MatchOutcome(int winningTeam, Reason reason) {

    public enum Reason {
        LAST_TEAM_STANDING,
        TIME_LIMIT,
        FORFEIT,
        OBJECTIVE,   // a mode's own win condition: a crystal broken, a payload delivered
        DRAW,
        ABORTED
    }

    /** No winner. */
    public static final int NO_TEAM = -1;

    public static MatchOutcome win(int team, Reason reason) {
        return new MatchOutcome(team, reason);
    }

    /**
     * Nobody won, and that is a <b>result</b>.
     *
     * Not the same thing as an abort, and the difference is money: a draw is rated (both
     * teams score 0.5 in Elo), an abort is not. Thirty minutes of Fortress that ends level on
     * kills is a draw. A server restart is an abort. Collapsing the two would either wipe an
     * hour of play off the ladder or hand people a rating change for a match nobody finished.
     */
    public static MatchOutcome draw() {
        return new MatchOutcome(NO_TEAM, Reason.DRAW);
    }

    public static MatchOutcome aborted() {
        return new MatchOutcome(NO_TEAM, Reason.ABORTED);
    }

    public boolean hasWinner() {
        return winningTeam != NO_TEAM;
    }

    public boolean isDraw() {
        return reason == Reason.DRAW;
    }

    /** Does this touch the ladder at all? A draw does. An abort does not. */
    public boolean isRated() {
        return hasWinner() || isDraw();
    }
}

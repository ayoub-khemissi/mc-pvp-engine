package fr.ayoub.pvp.domain.rating;

/** The result of a match from one team's point of view. */
public enum Outcome {

    WIN(1.0),
    DRAW(0.5),
    LOSS(0.0);

    private final double score;

    Outcome(double score) {
        this.score = score;
    }

    /** The Elo "score": 1 for a win, 0.5 for a draw, 0 for a loss. */
    public double score() {
        return score;
    }
}

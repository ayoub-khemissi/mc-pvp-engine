package fr.ayoub.pvp.domain.rating;

import java.util.List;

/**
 * A team is rated by the average of its members.
 * This is what makes 1v1 and 5v5 share the same Elo maths.
 */
public final class TeamRating {

    private TeamRating() {
    }

    public static int average(List<Integer> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            throw new IllegalArgumentException("a team must have at least one player");
        }
        long sum = 0;
        for (int rating : ratings) {
            sum += rating;
        }
        return (int) Math.round((double) sum / ratings.size());
    }
}

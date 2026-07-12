package fr.ayoub.pvp.domain.rating;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Turns a rating into a rank the player actually cares about.
 *
 * "1247" means nothing to most people; "Gold" does.
 */
public final class DivisionLadder {

    private final List<Division> divisions;   // ascending by minRating

    public DivisionLadder(List<Division> divisions) {
        if (divisions.isEmpty()) {
            throw new IllegalArgumentException("a ladder needs at least one division");
        }

        List<Division> sorted = divisions.stream()
                .sorted(Comparator.comparingInt(Division::minRating))
                .toList();

        if (sorted.getFirst().minRating() > 0) {
            throw new IllegalArgumentException(
                    "the lowest division must start at 0 or below, so every rating has a division");
        }
        this.divisions = sorted;
    }

    public static DivisionLadder standard() {
        return new DivisionLadder(List.of(
                new Division("bronze", "Bronze", 0),
                new Division("silver", "Silver", 1000),
                new Division("gold", "Gold", 1200),
                new Division("platinum", "Platinum", 1400),
                new Division("diamond", "Diamond", 1600),
                new Division("master", "Master", 1800),
                new Division("grandmaster", "Grandmaster", 2000)));
    }

    public List<Division> divisions() {
        return divisions;
    }

    /** The division a rating belongs to. Always finds one. */
    public Division of(int rating) {
        Division found = divisions.getFirst();
        for (Division division : divisions) {
            if (rating >= division.minRating()) {
                found = division;
            } else {
                break;
            }
        }
        return found;
    }

    /** The next rank up — what the player is climbing towards. */
    public Optional<Division> next(int rating) {
        return divisions.stream()
                .filter(division -> division.minRating() > rating)
                .findFirst();
    }
}

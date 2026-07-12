package fr.ayoub.pvp.domain.arena;

import java.util.Set;

/**
 * What a map says about itself, so the engine can decide when to use it.
 *
 * @param modes     which game modes may use it; <b>empty = any mode</b>
 * @param minRating lowest rating this map is meant for (inclusive)
 * @param maxRating highest rating this map is meant for (inclusive)
 */
public record MapDescriptor(String id, Set<String> modes, int minRating, int maxRating) {

    public MapDescriptor {
        modes = Set.copyOf(modes);
        if (minRating > maxRating) {
            throw new IllegalArgumentException("map '" + id + "': minRating > maxRating");
        }
    }

    public boolean supports(String modeId) {
        return modes.isEmpty() || modes.contains(modeId);
    }

    public boolean fits(int rating) {
        return rating >= minRating && rating <= maxRating;
    }

    /** How narrow the rating band is. Narrower = more specific = preferred. */
    public long bandWidth() {
        return (long) maxRating - minRating;
    }
}

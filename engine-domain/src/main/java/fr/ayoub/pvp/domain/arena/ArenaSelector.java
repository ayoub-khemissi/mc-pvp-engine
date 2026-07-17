package fr.ayoub.pvp.domain.arena;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chooses the map a match is played on — Clash-Royale style: your rating decides
 * which arena you fight in.
 *
 * Two rules, and they are not equal:
 * <ul>
 *   <li><b>The mode is a hard rule.</b> A dodgeball court never hosts a duel.</li>
 *   <li><b>The rating band is a preference.</b> If no band covers the players, we
 *       still put them on a compatible map rather than cancel their match — waiting
 *       in a queue forever is worse than playing on the "wrong" arena.</li>
 * </ul>
 *
 * Pure — the caller passes only the maps that are currently free.
 */
public final class ArenaSelector {

    private ArenaSelector() {
    }

    public static Optional<MapDescriptor> select(List<MapDescriptor> freeMaps, String modeId, int rating) {
        return select(freeMaps, modeId, rating, false);
    }

    /**
     * @param dedicatedOnly the mode refuses a general-purpose map.
     *                      <p>
     *                      A map that names no mode means "any mode", which is a fine default
     *                      for a plain arena — until a mode turns up that cannot possibly be
     *                      played on one. Fortress needs two fortress pads, resources and a
     *                      destructible floor; dropped onto a duel island it produces a match
     *                      that starts and can never be won. Such a mode says so, and then it
     *                      only ever lands on a map that names it.
     */
    public static Optional<MapDescriptor> select(List<MapDescriptor> freeMaps, String modeId,
                                                 int rating, boolean dedicatedOnly) {
        // The narrowest band is the most deliberate choice; id breaks a tie. (candidates() drops
        // the narrowest-band preference on purpose, so a random pick can range over all of them.)
        return candidates(freeMaps, modeId, rating, dedicatedOnly).stream()
                .min(Comparator.comparingLong(MapDescriptor::bandWidth)
                        .thenComparing(MapDescriptor::id));
    }

    /**
     * <b>Every</b> map that could host this match, not just the one best pick — so the caller can
     * choose among them (at random, say, to cycle the arenas instead of always landing on the same
     * one). The eligibility rules are the same as {@link #select}: the mode is hard, the rating band
     * is a preference. If any map is made for this rating, only those are returned; if none is, all
     * compatible maps are, so a match is never refused for want of the perfect band.
     */
    public static List<MapDescriptor> candidates(List<MapDescriptor> freeMaps, String modeId,
                                                 int rating, boolean dedicatedOnly) {
        List<MapDescriptor> compatible = freeMaps.stream()
                .filter(map -> dedicatedOnly ? map.isDedicatedTo(modeId) : map.supports(modeId))
                .toList();

        if (compatible.isEmpty()) {
            return List.of();
        }

        List<MapDescriptor> inBand = compatible.stream()
                .filter(map -> map.fits(rating))
                .toList();
        if (!inBand.isEmpty()) {
            return inBand;
        }

        // Nothing made for this rating: the maps whose band is closest, so a random pick among
        // equally-close arenas is still a sensible one.
        long nearest = compatible.stream()
                .mapToLong(map -> distanceTo(map, rating))
                .min()
                .orElse(0);
        return compatible.stream()
                .filter(map -> distanceTo(map, rating) == nearest)
                .toList();
    }

    private static long distanceTo(MapDescriptor map, int rating) {
        if (rating < map.minRating()) {
            return (long) map.minRating() - rating;
        }
        if (rating > map.maxRating()) {
            return (long) rating - map.maxRating();
        }
        return 0;
    }
}

package fr.ayoub.pvp.domain.br;

/**
 * The storm at one instant: everything the world border and the HUD need to be told.
 *
 * @param phase             which phase we are in (0-based)
 * @param mode              holding the ring, closing it, or done
 * @param radius            the ring's radius right now — interpolated while closing
 * @param damagePerTick     what a caught player is losing per tick right now
 * @param secondsUntilNext  seconds until the next transition: until the close starts (while holding),
 *                          or until it finishes (while closing). 0 when finished
 * @param finished          the last ring has closed; it just stays there and keeps killing
 */
public record StormState(int phase, Mode mode, double radius, double damagePerTick,
                         int secondsUntilNext, boolean finished) {

    public enum Mode {
        /** The ring is still; players have time to get inside. */
        HOLDING,
        /** The ring is moving inward. */
        CLOSING,
        /** The last ring has closed and is not moving again. */
        FINISHED
    }

    /** Is a point this far from the centre in the storm right now? On the ring is safe. */
    public boolean isOutside(double distanceFromCentre) {
        return distanceFromCentre > radius;
    }
}

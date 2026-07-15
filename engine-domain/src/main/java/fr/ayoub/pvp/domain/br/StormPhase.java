package fr.ayoub.pvp.domain.br;

/**
 * One tightening of the storm: hold the ring, then close it to a smaller one, taking a set
 * amount of damage off anyone caught outside meanwhile.
 *
 * @param holdSeconds    how long the current ring stays put before it starts closing — the run-for-it
 *                       window, and what the countdown on the HUD counts down
 * @param closeSeconds   how long the ring takes to close to {@link #radius}. Must be > 0: an instant
 *                       ring that teleports over players is not a storm, it is a guillotine
 * @param radius         the ring this phase closes <b>to</b>. Always smaller than the one before —
 *                       a storm only ever tightens
 * @param damagePerTick  what a player outside the ring loses per damage tick during this phase.
 *                       Rises phase to phase: a nuisance at first, lethal at the end
 */
public record StormPhase(int holdSeconds, int closeSeconds, double radius, double damagePerTick) {

    public StormPhase {
        if (holdSeconds < 0) {
            throw new IllegalArgumentException("hold cannot be negative, was " + holdSeconds);
        }
        if (closeSeconds <= 0) {
            throw new IllegalArgumentException("a close must take time, was " + closeSeconds);
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive, was " + radius);
        }
        if (damagePerTick < 0) {
            throw new IllegalArgumentException("damage cannot be negative, was " + damagePerTick);
        }
    }

    public int seconds() {
        return holdSeconds + closeSeconds;
    }
}

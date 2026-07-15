package fr.ayoub.pvp.domain.br;

import java.util.List;

/**
 * The whole storm, as a pure function of elapsed time.
 *
 * <p>A battle royale zone is a sequence of {@link StormPhase}s: the ring holds, then closes to a
 * smaller ring, and the damage climbs each phase. Because it always closes toward the centre, the
 * entire thing is a list of radii and durations — no moving centre, no Bukkit, no clock. Hand
 * {@link #at} the seconds since the match started and it returns the {@link StormState}: the radius
 * now, whether it is holding or closing, what a caught player is taking, and how long until the next
 * transition. That is exactly what the world border ({@code setSize}) and the HUD countdown consume.
 *
 * <p>Phase {@code i} closes <b>from</b> the radius before it — the initial radius for phase 0, the
 * previous phase's radius otherwise — <b>to</b> its own. A storm only ever tightens, which the
 * constructor enforces: a ring that opened outward would be a bug nobody would think to test for in
 * the field.
 */
public final class StormSchedule {

    private final double initialRadius;
    private final List<StormPhase> phases;

    public StormSchedule(double initialRadius, List<StormPhase> phases) {
        if (initialRadius <= 0) {
            throw new IllegalArgumentException("the initial radius must be positive");
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("a storm needs at least one phase");
        }

        double previous = initialRadius;
        for (StormPhase phase : phases) {
            if (phase.radius() > previous) {
                throw new IllegalArgumentException("the storm grows at radius " + phase.radius()
                        + " (was " + previous + ") — a ring only ever closes inward");
            }
            previous = phase.radius();
        }

        this.initialRadius = initialRadius;
        this.phases = List.copyOf(phases);
    }

    public double initialRadius() {
        return initialRadius;
    }

    /** How long the storm runs from full ring to final ring. */
    public int totalSeconds() {
        return phases.stream().mapToInt(StormPhase::seconds).sum();
    }

    /** The storm at {@code elapsedSeconds} into the match. */
    public StormState at(int elapsedSeconds) {
        double before = initialRadius;
        int start = 0;

        for (int i = 0; i < phases.size(); i++) {
            StormPhase phase = phases.get(i);

            int holdEnd = start + phase.holdSeconds();
            int closeEnd = holdEnd + phase.closeSeconds();

            if (elapsedSeconds < holdEnd) {
                return new StormState(i, StormState.Mode.HOLDING, before,
                        phase.damagePerTick(), holdEnd - elapsedSeconds, false);
            }
            if (elapsedSeconds < closeEnd) {
                double progress = (double) (elapsedSeconds - holdEnd) / phase.closeSeconds();
                double radius = before + (phase.radius() - before) * progress;
                return new StormState(i, StormState.Mode.CLOSING, radius,
                        phase.damagePerTick(), closeEnd - elapsedSeconds, false);
            }

            before = phase.radius();
            start = closeEnd;
        }

        // Past the last close: the final ring, holding forever, still lethal.
        StormPhase last = phases.get(phases.size() - 1);
        return new StormState(phases.size() - 1, StormState.Mode.FINISHED,
                last.radius(), last.damagePerTick(), 0, true);
    }
}

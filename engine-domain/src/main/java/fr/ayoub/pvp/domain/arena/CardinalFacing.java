package fr.ayoub.pvp.domain.arena;

/**
 * Snapping a look direction to one of the four straight ones: 0, 90, 180, 270.
 *
 * <p>A spawn wants to face dead-on down an axis, so a fighter squaring up looks straight at the
 * arena and at the enemy, not off at 47° into a wall. The admin only has to stand and look roughly
 * the right way; {@link #snap} rounds it.
 *
 * <p>Minecraft yaw: 0 south (+Z), 90 west (−X), 180 north (−Z), 270 east (+X). {@link #opposite} is
 * the half-turn that makes two teams face each other.
 */
public final class CardinalFacing {

    private CardinalFacing() {
    }

    /** The nearest of 0, 90, 180, 270. A tie rounds up. */
    public static float snap(float yaw) {
        float quarters = Math.round(normalize(yaw) / 90f);   // 0..4
        return (quarters * 90f) % 360f;
    }

    /** The half-turn: 0↔180, 90↔270. Assumes a value already snapped. */
    public static float opposite(float cardinal) {
        return (normalize(cardinal) + 180f) % 360f;
    }

    private static float normalize(float yaw) {
        float y = yaw % 360f;
        return y < 0 ? y + 360f : y;
    }
}

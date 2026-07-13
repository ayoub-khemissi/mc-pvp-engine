package fr.ayoub.pvp.domain.mode;

import java.util.Comparator;
import java.util.Objects;

/**
 * A game mode's place in the menu: its id, the rank it asks for, and whether it is on.
 *
 * The rank is a <b>declared</b> position, not a list index: duel asks for 1, fortress for
 * 2, payload for 3. Turning duel off does not renumber anybody — fortress simply becomes
 * the first thing left, and duel goes back to the top when it is turned on again.
 */
public record ModeSlot(String id, int order, boolean enabled) {

    /** By rank; ties by id, so the menu never depends on plugin load order. */
    public static final Comparator<ModeSlot> BY_RANK =
            Comparator.comparingInt(ModeSlot::order).thenComparing(ModeSlot::id);

    public ModeSlot {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a mode needs an id");
        }
    }

    public ModeSlot enabled(boolean value) {
        return new ModeSlot(id, order, value);
    }
}

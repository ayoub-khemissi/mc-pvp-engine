package fr.ayoub.pvp.domain.match;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which player a locked spectator's camera moves to next.
 *
 * <p>A spectator of a hidden-information mode does not free-fly through the walls — their camera is
 * pinned to a player, and they see exactly what that player sees. Pressing sneak moves them along
 * the ring. This is that step, as pure list arithmetic.
 *
 * <p>It is keyed on <b>who</b> they are watching, not on a remembered index, because the list of
 * watchable players changes underneath it: someone disconnects, someone rejoins. If the player
 * they were watching is no longer in it, the camera restarts at the first rather than following an
 * index that now points at whoever took that slot.
 */
public final class SpectatorRing {

    private SpectatorRing() {
    }

    public static Optional<UUID> next(List<UUID> candidates, UUID current) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // -1 when they are watching nobody (current == null) or a player who has left the list —
        // spelled out because List.of() throws on indexOf(null) rather than returning -1.
        int here = current == null ? -1 : candidates.indexOf(current);
        int then = (here + 1) % candidates.size();
        return Optional.of(candidates.get(then));
    }
}

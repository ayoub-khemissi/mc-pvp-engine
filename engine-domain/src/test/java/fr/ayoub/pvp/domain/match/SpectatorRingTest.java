package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which player a locked spectator's camera moves to next.
 *
 * <p>A spectator of a hidden-information mode is not allowed to free-fly through the walls, so
 * their camera is pinned to a player and they see exactly what that player sees. Pressing sneak
 * moves them along to the next one — this decides who "next" is, as pure list arithmetic so it can
 * be tested without a server.
 *
 * <p>The list can change under it between one press and the next: players disconnect, are added by
 * the match. So "next" is defined by the <b>identity</b> of who they are watching. When that
 * identity is no longer in the list — the player they were watching has left the match — the
 * camera restarts at the first, rather than trusting an index that now points at somebody else.
 */
class SpectatorRingTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void startsAtTheFirstWhenWatchingNobody() {
        assertEquals(Optional.of(a), SpectatorRing.next(List.of(a, b, c), null));
    }

    @Test
    void movesToTheNext() {
        assertEquals(Optional.of(b), SpectatorRing.next(List.of(a, b, c), a));
        assertEquals(Optional.of(c), SpectatorRing.next(List.of(a, b, c), b));
    }

    @Test
    void wrapsAroundAtTheEnd() {
        assertEquals(Optional.of(a), SpectatorRing.next(List.of(a, b, c), c));
    }

    /**
     * The one that matters. The player they were watching left the match, so their identity is no
     * longer in the list — the camera restarts at the first rather than following a stale index
     * that would now be pointing at whoever slid into that slot.
     */
    @Test
    void restartsWhenTheWatchedPlayerIsGone() {
        assertEquals(Optional.of(a), SpectatorRing.next(List.of(a, c), b));
        assertEquals(Optional.of(a), SpectatorRing.next(List.of(a, b), c));
    }

    @Test
    void hasNobodyToWatchWhenTheMatchIsEmpty() {
        assertTrue(SpectatorRing.next(List.of(), a).isEmpty());
    }

    @Test
    void staysOnTheOnlyPlayerRatherThanFlicker() {
        // A single-player match: "next" is the same player, not empty, so the camera holds.
        assertEquals(Optional.of(a), SpectatorRing.next(List.of(a), a));
    }
}

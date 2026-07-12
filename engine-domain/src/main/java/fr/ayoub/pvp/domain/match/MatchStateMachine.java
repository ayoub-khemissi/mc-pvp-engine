package fr.ayoub.pvp.domain.match;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Guards the order of a match.
 *
 * A match that jumps straight to LIVE without preparing, or that "un-ends" itself,
 * is a bug — this turns it into a loud exception instead of a half-broken arena and
 * players stuck in limbo.
 *
 * A match can always be aborted (→ CLEANUP): someone disconnects, a map is broken,
 * the server is stopping.
 */
public final class MatchStateMachine {

    private static final Map<MatchState, Set<MatchState>> ALLOWED = new EnumMap<>(MatchState.class);

    static {
        ALLOWED.put(MatchState.CREATED, Set.of(MatchState.PREPARING, MatchState.CLEANUP));
        ALLOWED.put(MatchState.PREPARING, Set.of(MatchState.COUNTDOWN, MatchState.CLEANUP));
        ALLOWED.put(MatchState.COUNTDOWN, Set.of(MatchState.LIVE, MatchState.CLEANUP));
        ALLOWED.put(MatchState.LIVE, Set.of(MatchState.ROUND_ENDING, MatchState.ENDING, MatchState.CLEANUP));
        // a new round starts with a fresh countdown; otherwise the match is over
        ALLOWED.put(MatchState.ROUND_ENDING, Set.of(MatchState.COUNTDOWN, MatchState.ENDING, MatchState.CLEANUP));
        ALLOWED.put(MatchState.ENDING, Set.of(MatchState.CLEANUP));
        ALLOWED.put(MatchState.CLEANUP, Set.of(MatchState.CLOSED));
        ALLOWED.put(MatchState.CLOSED, Set.of());
    }

    private MatchState state = MatchState.CREATED;

    public MatchState state() {
        return state;
    }

    public boolean canTransitionTo(MatchState next) {
        return ALLOWED.get(state).contains(next);
    }

    public void transitionTo(MatchState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("a match cannot go from " + state + " to " + next);
        }
        state = next;
    }

    public boolean isLive() {
        return state == MatchState.LIVE;
    }

    public boolean isFinished() {
        return state == MatchState.CLOSED;
    }
}

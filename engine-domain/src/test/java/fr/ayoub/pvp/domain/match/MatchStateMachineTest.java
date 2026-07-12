package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchStateMachineTest {

    @Test
    void aMatchStartsOutCreated() {
        assertEquals(MatchState.CREATED, new MatchStateMachine().state());
    }

    @Test
    void theHappyPathRunsToTheEnd() {
        MatchStateMachine match = new MatchStateMachine();

        match.transitionTo(MatchState.PREPARING);
        match.transitionTo(MatchState.COUNTDOWN);
        match.transitionTo(MatchState.LIVE);
        match.transitionTo(MatchState.ENDING);
        match.transitionTo(MatchState.CLEANUP);
        match.transitionTo(MatchState.CLOSED);

        assertEquals(MatchState.CLOSED, match.state());
    }

    @Test
    void roundsLoopBackToTheCountdown() {
        MatchStateMachine match = new MatchStateMachine();
        match.transitionTo(MatchState.PREPARING);
        match.transitionTo(MatchState.COUNTDOWN);
        match.transitionTo(MatchState.LIVE);

        // round 1 ends, round 2 begins
        match.transitionTo(MatchState.ROUND_ENDING);
        match.transitionTo(MatchState.COUNTDOWN);
        match.transitionTo(MatchState.LIVE);

        assertEquals(MatchState.LIVE, match.state());
    }

    @Test
    void aMatchCannotStartFightingBeforeItIsPrepared() {
        MatchStateMachine match = new MatchStateMachine();

        assertThrows(IllegalStateException.class, () -> match.transitionTo(MatchState.LIVE));
    }

    @Test
    void aFinishedMatchIsFinished() {
        MatchStateMachine match = new MatchStateMachine();
        match.transitionTo(MatchState.PREPARING);
        match.transitionTo(MatchState.CLEANUP);   // abort
        match.transitionTo(MatchState.CLOSED);

        assertThrows(IllegalStateException.class, () -> match.transitionTo(MatchState.LIVE));
        assertTrue(match.isFinished());
    }

    @Test
    void aMatchCanBeAbortedFromAnywhere() {
        MatchStateMachine match = new MatchStateMachine();
        match.transitionTo(MatchState.PREPARING);
        match.transitionTo(MatchState.COUNTDOWN);
        match.transitionTo(MatchState.LIVE);

        // someone disconnected, a map broke, the server is stopping…
        assertTrue(match.canTransitionTo(MatchState.CLEANUP));
        match.transitionTo(MatchState.CLEANUP);

        assertEquals(MatchState.CLEANUP, match.state());
    }

    @Test
    void goingBackwardsIsRefused() {
        MatchStateMachine match = new MatchStateMachine();
        match.transitionTo(MatchState.PREPARING);
        match.transitionTo(MatchState.COUNTDOWN);
        match.transitionTo(MatchState.LIVE);

        assertFalse(match.canTransitionTo(MatchState.PREPARING));
        assertThrows(IllegalStateException.class, () -> match.transitionTo(MatchState.PREPARING));
    }
}

package fr.ayoub.pvp.domain.fortress;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortressVoteTest {

    private final UUID best = UUID.randomUUID();
    private final UUID middle = UUID.randomUUID();
    private final UUID worst = UUID.randomUUID();

    private static Candidate fortressOf(UUID owner, String name) {
        return new Candidate(owner, UUID.randomUUID(), name);
    }

    private final Candidate bestsKeep = fortressOf(best, "Keep");
    private final Candidate middlesTower = fortressOf(middle, "Tower");
    private final Candidate worstsBunker = fortressOf(worst, "Bunker");

    /** Candidates are shown left to right, best-rated player first: 1, 2, 3. */
    private TeamVote threeCandidates() {
        return new TeamVote(List.of(bestsKeep, middlesTower, worstsBunker));
    }

    @Test
    void theCandidatesAreNumberedFromOneLeftToRight() {
        TeamVote vote = threeCandidates();

        assertEquals(3, vote.candidates().size());
        assertEquals(bestsKeep, vote.candidate(1));
        assertEquals(middlesTower, vote.candidate(2));
        assertEquals(worstsBunker, vote.candidate(3));
    }

    @Test
    void aMajorityWins() {
        TeamVote vote = threeCandidates();

        vote.cast(best, 2);
        vote.cast(middle, 2);
        vote.cast(worst, 3);

        assertEquals(middlesTower, vote.result());
        assertEquals(2, vote.tally(2));
        assertEquals(1, vote.tally(3));
        assertEquals(0, vote.tally(1));
    }

    @Test
    void aTieGoesToTheHighestRatedPlayersFortress() {
        // 1–1 in a 2v2 is not an edge case, it is Tuesday.
        TeamVote vote = new TeamVote(List.of(bestsKeep, middlesTower));

        vote.cast(best, 2);
        vote.cast(middle, 1);

        assertEquals(bestsKeep, vote.result(),
                "candidate 1 is the best-rated player's fortress, so it takes the tie");
    }

    @Test
    void ifNobodyVotesTheBestRatedPlayersFortressIsUsed() {
        TeamVote vote = threeCandidates();

        assertEquals(bestsKeep, vote.result());
    }

    @Test
    void aPlayerCanChangeTheirMind() {
        TeamVote vote = threeCandidates();

        vote.cast(worst, 3);
        vote.cast(worst, 2);

        assertEquals(0, vote.tally(3));
        assertEquals(1, vote.tally(2));
    }

    @Test
    void aVoteForNothingIsIgnored() {
        TeamVote vote = threeCandidates();

        vote.cast(best, 0);
        vote.cast(best, 4);
        vote.cast(best, -1);

        assertFalse(vote.hasVoted(best));
    }

    @Test
    void theTeamKnowsWhoHasVoted() {
        TeamVote vote = threeCandidates();

        assertFalse(vote.hasVoted(best));
        assertEquals(0, vote.votesCast());

        vote.cast(best, 1);

        assertTrue(vote.hasVoted(best));
        assertEquals(1, vote.votesCast());
    }

    @Test
    void aTeamIsReadyOnceEveryoneHasVoted() {
        TeamVote vote = threeCandidates();

        vote.cast(best, 1);
        vote.cast(middle, 1);
        assertFalse(vote.ready(3), "one player has not voted yet");

        vote.cast(worst, 1);
        assertTrue(vote.ready(3));
    }

    @Test
    void aTeamWithOnePlayerIsReadyAsSoonAsTheyVote() {
        TeamVote vote = new TeamVote(List.of(bestsKeep));

        assertFalse(vote.ready(1));
        vote.cast(best, 1);
        assertTrue(vote.ready(1));
    }

    /**
     * Nobody in the team has a playable fortress. The match still has to happen, so the
     * engine hands us a preset — there is always at least one candidate.
     */
    @Test
    void aTeamAlwaysHasSomethingToPlay() {
        Candidate preset = fortressOf(null, "Preset: Bastion");
        TeamVote vote = new TeamVote(List.of(preset));

        assertEquals(preset, vote.result());
        assertTrue(vote.ready(0), "an empty team cannot hold up the vote");
    }

    @Test
    void aVoteNeedsAtLeastOneCandidate() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TeamVote(List.of()));
    }
}

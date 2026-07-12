package fr.ayoub.pvp.domain.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyRegistryTest {

    private static final long NOW = 1_000_000L;
    private static final long INVITE_TTL = 60_000L;

    private final PartyRegistry registry = new PartyRegistry(5, INVITE_TTL);

    private final UUID leader = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void aPlayerWithNoPartyHasNoParty() {
        assertTrue(registry.partyOf(leader).isEmpty());
    }

    @Test
    void invitingSomeoneCreatesThePartyOnTheSpot() {
        registry.invite(leader, friend, NOW);

        assertTrue(registry.partyOf(leader).isPresent(), "you lead a party as soon as you invite");
        assertEquals(1, registry.partyOf(leader).orElseThrow().size(), "the invitee is not a member yet");
    }

    @Test
    void acceptingAnInviteJoinsTheParty() {
        registry.invite(leader, friend, NOW);

        assertTrue(registry.accept(friend, NOW + 1000));

        Party party = registry.partyOf(friend).orElseThrow();
        assertSame(party, registry.partyOf(leader).orElseThrow(), "both are in the same party");
        assertTrue(party.contains(friend));
    }

    @Test
    void anInviteExpires() {
        registry.invite(leader, friend, NOW);

        assertFalse(registry.accept(friend, NOW + INVITE_TTL + 1));
        assertTrue(registry.partyOf(friend).isEmpty());
    }

    @Test
    void thereIsNothingToAcceptWithoutAnInvite() {
        assertFalse(registry.accept(stranger, NOW));
    }

    @Test
    void anInviteIsConsumedOnce() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);
        registry.leave(friend);

        assertFalse(registry.accept(friend, NOW), "the invite must not let them walk back in");
    }

    @Test
    void onlyTheLeaderMayInvite() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        assertFalse(registry.invite(friend, stranger, NOW), "a member is not the leader");
        assertTrue(registry.pendingInvite(stranger).isEmpty());
    }

    @Test
    void aPlayerAlreadyInAPartyCannotBeInvited() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        UUID rival = UUID.randomUUID();
        assertFalse(registry.invite(rival, friend, NOW));
    }

    @Test
    void aFullPartyCannotInvite() {
        PartyRegistry small = new PartyRegistry(2, INVITE_TTL);
        small.invite(leader, friend, NOW);
        small.accept(friend, NOW);

        assertFalse(small.invite(leader, stranger, NOW));
    }

    @Test
    void leavingRemovesYouFromTheParty() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        registry.leave(friend);

        assertTrue(registry.partyOf(friend).isEmpty());
        assertEquals(1, registry.partyOf(leader).orElseThrow().size());
    }

    @Test
    void aPartyThatEmptiesOutIsGone() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        registry.leave(friend);
        registry.leave(leader);

        assertTrue(registry.partyOf(leader).isEmpty());
        assertEquals(0, registry.count());
    }

    @Test
    void whenTheLeaderLeavesThePartyLivesOn() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        registry.leave(leader);

        Party party = registry.partyOf(friend).orElseThrow();
        assertTrue(party.isLeader(friend), "the friend inherits it");
        assertTrue(registry.partyOf(leader).isEmpty());
    }

    @Test
    void disbandingClearsEveryone() {
        registry.invite(leader, friend, NOW);
        registry.accept(friend, NOW);

        registry.disband(registry.partyOf(leader).orElseThrow());

        assertTrue(registry.partyOf(leader).isEmpty());
        assertTrue(registry.partyOf(friend).isEmpty());
        assertEquals(0, registry.count());
    }

    @Test
    void aPendingInviteIsVisibleToTheInvitee() {
        registry.invite(leader, friend, NOW);

        assertEquals(leader, registry.pendingInvite(friend).orElseThrow().from());
        assertTrue(registry.pendingInvite(stranger).isEmpty());
    }

    @Test
    void anExpiredInviteIsNotShownEither() {
        registry.invite(leader, friend, NOW);
        registry.expire(NOW + INVITE_TTL + 1);

        assertTrue(registry.pendingInvite(friend).isEmpty());
    }
}

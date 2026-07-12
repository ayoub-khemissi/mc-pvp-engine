package fr.ayoub.pvp.domain.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void aNewPartyIsJustItsLeader() {
        Party party = new Party(leader, 5);

        assertEquals(1, party.size());
        assertTrue(party.isLeader(leader));
        assertTrue(party.contains(leader));
        assertEquals(leader, party.members().getFirst(), "the leader is always listed first");
    }

    @Test
    void aFriendCanJoin() {
        Party party = new Party(leader, 5);
        party.add(friend);

        assertEquals(2, party.size());
        assertTrue(party.contains(friend));
        assertFalse(party.isLeader(friend));
    }

    @Test
    void joiningTwiceChangesNothing() {
        Party party = new Party(leader, 5);
        party.add(friend);
        party.add(friend);

        assertEquals(2, party.size());
    }

    @Test
    void aPartyCannotGrowPastItsLimit() {
        Party party = new Party(leader, 2);
        party.add(friend);

        assertTrue(party.isFull());
        assertThrows(IllegalStateException.class, () -> party.add(other));
    }

    @Test
    void aMemberCanLeave() {
        Party party = new Party(leader, 5);
        party.add(friend);
        party.remove(friend);

        assertEquals(1, party.size());
        assertFalse(party.contains(friend));
        assertTrue(party.isLeader(leader), "the leader is untouched");
    }

    @Test
    void whenTheLeaderLeavesTheNextMemberTakesOver() {
        Party party = new Party(leader, 5);
        party.add(friend);
        party.add(other);

        party.remove(leader);

        assertTrue(party.isLeader(friend), "the oldest remaining member inherits the party");
        assertEquals(2, party.size());
        assertEquals(friend, party.members().getFirst());
    }

    @Test
    void aPartyThatLosesEveryoneIsEmpty() {
        Party party = new Party(leader, 5);
        party.remove(leader);

        assertTrue(party.isEmpty(), "an empty party must be disbanded by its owner");
    }

    @Test
    void theMemberListCannotBeEditedFromOutside() {
        Party party = new Party(leader, 5);

        assertThrows(UnsupportedOperationException.class, () -> party.members().add(friend));
    }
}

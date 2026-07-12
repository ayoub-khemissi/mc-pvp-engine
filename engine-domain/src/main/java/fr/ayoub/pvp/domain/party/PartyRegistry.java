package fr.ayoub.pvp.domain.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is in which party, and who has been invited where.
 *
 * Every rule that can be broken by a player lives here — inviting when you are not the
 * leader, joining a full party, accepting an invite that has gone stale, being in two
 * parties at once. The Bukkit layer only reports the answer.
 *
 * Pure: the time is always passed in, so all of this is unit-tested with no server.
 */
public final class PartyRegistry {

    private final int maxPartySize;
    private final long inviteTtlMillis;

    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();   // invitee -> invite

    public PartyRegistry(int maxPartySize, long inviteTtlMillis) {
        this.maxPartySize = maxPartySize;
        this.inviteTtlMillis = inviteTtlMillis;
    }

    public int maxPartySize() {
        return maxPartySize;
    }

    public Optional<Party> partyOf(UUID player) {
        return Optional.ofNullable(byPlayer.get(player));
    }

    public Optional<Invite> pendingInvite(UUID player) {
        return Optional.ofNullable(invites.get(player));
    }

    /** Number of live parties — used by the tests and the admin readout. */
    public int count() {
        return (int) byPlayer.values().stream().distinct().count();
    }

    /**
     * Invite someone. The party is created on the first invite, so a player never has to
     * "make a party" as a separate step.
     *
     * @return false if the rules say no (not the leader, party full, target already
     *         in a party, inviting yourself)
     */
    public boolean invite(UUID inviter, UUID target, long nowMillis) {
        if (inviter.equals(target) || byPlayer.containsKey(target)) {
            return false;
        }

        Party party = byPlayer.get(inviter);
        if (party == null) {
            party = new Party(inviter, maxPartySize);
            byPlayer.put(inviter, party);
        } else if (!party.isLeader(inviter) || party.isFull()) {
            return false;
        }

        invites.put(target, new Invite(inviter, party.id(), nowMillis));
        return true;
    }

    /** @return false if there is nothing to accept, it went stale, or the party filled up. */
    public boolean accept(UUID player, long nowMillis) {
        Invite invite = invites.get(player);
        if (invite == null) {
            return false;
        }
        invites.remove(player);   // one shot, valid or not

        if (invite.hasExpired(nowMillis, inviteTtlMillis)) {
            return false;
        }

        Party party = byPlayer.get(invite.from());
        if (party == null || !party.id().equals(invite.partyId()) || party.isFull()) {
            return false;   // the party died, or filled up, while the invite was in flight
        }

        party.add(player);
        byPlayer.put(player, party);
        return true;
    }

    public void decline(UUID player) {
        invites.remove(player);
    }

    /**
     * Leave — or be dropped on disconnect.
     *
     * A party of one is a perfectly normal state (you are alone in it the moment you send
     * your first invite), so it is only disbanded once the last member is gone.
     */
    public void leave(UUID player) {
        Party party = byPlayer.remove(player);
        if (party == null) {
            return;
        }
        party.remove(player);

        if (party.isEmpty()) {
            disband(party);
        }
    }

    public void disband(Party party) {
        for (UUID member : party.members()) {
            byPlayer.remove(member);
        }
        invites.values().removeIf(invite -> invite.partyId().equals(party.id()));
    }

    /** Called on the tick — drops invites nobody answered. */
    public void expire(long nowMillis) {
        invites.values().removeIf(invite -> invite.hasExpired(nowMillis, inviteTtlMillis));
    }

    /** Every live party. */
    public List<Party> parties() {
        return new ArrayList<>(byPlayer.values().stream().distinct().toList());
    }
}

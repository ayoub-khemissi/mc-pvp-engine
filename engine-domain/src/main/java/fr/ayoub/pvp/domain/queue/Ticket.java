package fr.ayoub.pvp.domain.queue;

import java.util.List;
import java.util.UUID;

/**
 * One entry in a queue.
 *
 * A ticket is a <b>group</b>, not a player: a lone player is simply a group of one.
 * Modelling it this way is what lets friends queue together — the matchmaker moves
 * whole tickets around, so a party can never be split across two teams.
 *
 * The rating is the group's rating (the average of its members). Deliberately not a
 * Bukkit Player: the matchmaker stays pure and testable.
 */
public record Ticket(UUID id, List<UUID> members, int rating, long joinedAtMillis) {

    public Ticket {
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("a ticket needs at least one player");
        }
    }

    /** A player queueing alone. */
    public static Ticket solo(UUID player, int rating, long joinedAtMillis) {
        return new Ticket(player, List.of(player), rating, joinedAtMillis);
    }

    /** How many players this ticket brings — a party of 3 takes 3 slots in a team. */
    public int size() {
        return members.size();
    }

    public boolean isParty() {
        return members.size() > 1;
    }

    public long waitedMillis(long nowMillis) {
        return Math.max(0, nowMillis - joinedAtMillis);
    }
}

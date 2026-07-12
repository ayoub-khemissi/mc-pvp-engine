package fr.ayoub.pvp.domain.queue;

import java.util.UUID;

/**
 * One entry in a queue: a player (later, a party) with their rating and when they joined.
 *
 * Deliberately not a Bukkit Player — the matchmaker stays pure and testable.
 */
public record Ticket(UUID id, int rating, long joinedAtMillis) {

    public long waitedMillis(long nowMillis) {
        return Math.max(0, nowMillis - joinedAtMillis);
    }
}

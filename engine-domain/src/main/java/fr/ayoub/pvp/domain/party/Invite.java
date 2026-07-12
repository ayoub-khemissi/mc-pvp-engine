package fr.ayoub.pvp.domain.party;

import java.util.UUID;

/** A pending invitation. It dies on its own, so a forgotten invite never blocks anyone. */
public record Invite(UUID from, UUID partyId, long sentAtMillis) {

    public boolean hasExpired(long nowMillis, long ttlMillis) {
        return nowMillis - sentAtMillis > ttlMillis;
    }
}

package fr.ayoub.pvp.storage;

import java.time.Instant;
import java.util.UUID;

/** A row of the {@code players} table. */
public record PlayerRecord(UUID uuid, String username, Instant firstSeen, Instant lastSeen) {
}

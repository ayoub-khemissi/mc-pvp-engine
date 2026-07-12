package fr.ayoub.pvp.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRepositoryTest {

    private DataSource ds;
    private PlayerRepository players;

    @BeforeEach
    void setUp() {
        ds = TestDatabase.migrated();
        players = new PlayerRepository(ds);
    }

    @Test
    void anUnknownPlayerIsNotFound() {
        assertTrue(players.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void aPlayerCanBeSavedAndReadBack() {
        UUID id = UUID.randomUUID();

        players.upsert(id, "Ayoub");

        Optional<PlayerRecord> found = players.find(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().uuid());
        assertEquals("Ayoub", found.get().username());
    }

    @Test
    void upsertingAgainUpdatesTheNameInsteadOfDuplicating() {
        UUID id = UUID.randomUUID();

        players.upsert(id, "OldName");
        players.upsert(id, "NewName");

        assertEquals("NewName", players.find(id).orElseThrow().username());
        assertEquals(1, TestDatabase.countRows(ds, "players"), "the player must not be duplicated");
    }
}

package fr.ayoub.pvp.mode.fortress.storage;

import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortressRepositoryTest {

    private FortressRepository fortresses;
    private UUID owner;

    @BeforeEach
    void setUp() {
        fortresses = new FortressRepository(TestDatabase.migrated());
        owner = UUID.randomUUID();
    }

    private static Blueprint aBuild() {
        Blueprint blueprint = new Blueprint(20);
        blueprint.set(5, 0, 5, "OBSIDIAN");
        blueprint.set(6, 0, 5, "STONE");
        blueprint.crystal(new BlockPos(5, 1, 5));
        return blueprint;
    }

    private SavedFortress save(int slot, String name, boolean playable, boolean isDefault) {
        SavedFortress fortress = new SavedFortress(owner, slot, name, aBuild(), playable, isDefault);
        fortresses.save(fortress);
        return fortress;
    }

    // --- the basics --------------------------------------------------------------

    @Test
    void aNewPlayerHasNoFortress() {
        assertTrue(fortresses.findAllFor(owner).isEmpty());
        assertTrue(fortresses.find(owner, 1).isEmpty());
        assertTrue(fortresses.findDefault(owner).isEmpty());
    }

    @Test
    void aFortressComesBackExactlyAsItWasBuilt() {
        save(1, "Keep", true, true);

        SavedFortress loaded = fortresses.find(owner, 1).orElseThrow();

        assertEquals("Keep", loaded.name());
        assertEquals(1, loaded.slot());
        assertTrue(loaded.playable());
        assertTrue(loaded.isDefault());

        Blueprint blueprint = loaded.blueprint();
        assertEquals(20, blueprint.size());
        assertEquals("OBSIDIAN", blueprint.get(5, 0, 5));
        assertEquals("STONE", blueprint.get(6, 0, 5));
        assertEquals(new BlockPos(5, 1, 5), blueprint.crystal());
    }

    @Test
    void savingTheSameSlotOverwritesIt() {
        save(1, "Keep", true, true);
        save(1, "Keep v2", false, true);

        assertEquals(1, fortresses.findAllFor(owner).size(), "one slot, one row");
        assertEquals("Keep v2", fortresses.find(owner, 1).orElseThrow().name());
        assertFalse(fortresses.find(owner, 1).orElseThrow().playable());
    }

    @Test
    void slotsAreListedInOrder() {
        save(3, "Third", true, false);
        save(1, "First", true, true);
        save(2, "Second", true, false);

        List<SavedFortress> all = fortresses.findAllFor(owner);

        assertEquals(List.of("First", "Second", "Third"), all.stream().map(SavedFortress::name).toList());
    }

    @Test
    void fortressesArePerPlayer() {
        save(1, "Mine", true, true);

        assertTrue(fortresses.findAllFor(UUID.randomUUID()).isEmpty());
    }

    @Test
    void aSlotCanBeEmptied() {
        save(1, "Keep", true, true);

        fortresses.delete(owner, 1);

        assertTrue(fortresses.find(owner, 1).isEmpty());
    }

    // --- exactly one default ------------------------------------------------------

    @Test
    void theDefaultIsTheOneTheMatchWillUse() {
        save(1, "Keep", true, true);
        save(2, "Tower", true, false);

        assertEquals("Keep", fortresses.findDefault(owner).orElseThrow().name());
    }

    @Test
    void choosingANewDefaultUnsetsTheOldOne() {
        save(1, "Keep", true, true);
        save(2, "Tower", true, false);

        fortresses.setDefault(owner, 2);

        assertEquals("Tower", fortresses.findDefault(owner).orElseThrow().name());
        assertFalse(fortresses.find(owner, 1).orElseThrow().isDefault());
        assertEquals(1, fortresses.findAllFor(owner).stream().filter(SavedFortress::isDefault).count(),
                "never two defaults");
    }

    @Test
    void savingANewDefaultUnsetsTheOldOne() {
        // The flag arrives with the row, not only through setDefault.
        save(1, "Keep", true, true);
        save(2, "Tower", true, true);

        assertEquals(1, fortresses.findAllFor(owner).stream().filter(SavedFortress::isDefault).count());
        assertEquals("Tower", fortresses.findDefault(owner).orElseThrow().name());
    }

    @Test
    void deletingTheDefaultPromotesAnotherSlot() {
        // Otherwise the player has fortresses but no default, and the match has nothing
        // to fall back on.
        save(1, "Keep", true, true);
        save(2, "Tower", true, false);

        fortresses.delete(owner, 1);

        assertEquals("Tower", fortresses.findDefault(owner).orElseThrow().name());
    }

    @Test
    void deletingTheLastFortressLeavesNoDefault() {
        save(1, "Keep", true, true);

        fortresses.delete(owner, 1);

        assertTrue(fortresses.findDefault(owner).isEmpty());
    }

    @Test
    void theFirstFortressSavedBecomesTheDefaultOnItsOwn() {
        // A player should never end up with a fortress and nothing to play.
        save(1, "Keep", true, false);

        assertEquals("Keep", fortresses.findDefault(owner).orElseThrow().name());
    }

    // --- what the menu needs without decoding everything --------------------------

    @Test
    void theSummaryIsStoredBesideTheBlueprint() {
        save(1, "Keep", true, true);

        SavedFortress loaded = fortresses.find(owner, 1).orElseThrow();

        assertEquals(2, loaded.blockCount(), "obsidian + stone");
        assertEquals(20, loaded.size());
    }

    @Test
    void onlyPlayableFortressesCanBeTakenIntoAMatch() {
        save(1, "Draft", false, true);
        save(2, "Ready", true, false);

        List<SavedFortress> playable = fortresses.findPlayableFor(owner);

        assertEquals(1, playable.size());
        assertEquals("Ready", playable.getFirst().name());
    }
}

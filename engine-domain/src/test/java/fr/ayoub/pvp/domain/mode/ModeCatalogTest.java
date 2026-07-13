package fr.ayoub.pvp.domain.mode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeCatalogTest {

    private final ModeCatalog catalog = new ModeCatalog();

    /** The three modes of the roadmap, each with the rank it declares. */
    private void registerAll() {
        catalog.register(new ModeSlot("duel", 1, true));
        catalog.register(new ModeSlot("fortress", 2, true));
        catalog.register(new ModeSlot("payload", 3, true));
    }

    private List<String> menu() {
        return catalog.active().stream().map(ModeSlot::id).toList();
    }

    @Test
    void anEmptyCatalogShowsNothing() {
        assertTrue(catalog.active().isEmpty());
    }

    @Test
    void modesAreListedInTheOrderTheyDeclare() {
        registerAll();

        assertEquals(List.of("duel", "fortress", "payload"), menu());
    }

    @Test
    void theOrderTheyLoadedInDoesNotMatter() {
        // Bukkit loads plugins alphabetically; the menu must not depend on that.
        catalog.register(new ModeSlot("payload", 3, true));
        catalog.register(new ModeSlot("duel", 1, true));
        catalog.register(new ModeSlot("fortress", 2, true));

        assertEquals(List.of("duel", "fortress", "payload"), menu());
    }

    @Test
    void aDisabledModeIsNotInTheMenu() {
        registerAll();
        catalog.setEnabled("fortress", false);

        assertEquals(List.of("duel", "payload"), menu());
    }

    @Test
    void disablingTheFirstModePromotesTheNextOne() {
        registerAll();
        catalog.setEnabled("duel", false);

        assertEquals(List.of("fortress", "payload"), menu());
        assertEquals("fortress", menu().getFirst(), "fortress becomes the first entry");
    }

    @Test
    void aDisabledModeCanComeBack() {
        registerAll();
        catalog.setEnabled("duel", false);
        catalog.setEnabled("duel", true);

        assertEquals(List.of("duel", "fortress", "payload"), menu(),
                "and it goes back to its own rank, not to the end");
    }

    @Test
    void everyModeCanBeOff() {
        registerAll();
        catalog.all().forEach(slot -> catalog.setEnabled(slot.id(), false));

        assertTrue(catalog.active().isEmpty());
        assertEquals(3, catalog.all().size(), "they are still registered, just off");
    }

    @Test
    void twoModesSharingARankAreOrderedByIdSoTheMenuIsStable() {
        catalog.register(new ModeSlot("zebra", 5, true));
        catalog.register(new ModeSlot("alpha", 5, true));

        assertEquals(List.of("alpha", "zebra"), menu());
    }

    @Test
    void aModeCanBeLookedUp() {
        registerAll();

        assertEquals(2, catalog.find("fortress").orElseThrow().order());
        assertTrue(catalog.find("nothing").isEmpty());
    }

    @Test
    void enablingSomethingThatDoesNotExistIsRefused() {
        assertFalse(catalog.setEnabled("ghost", true));
    }

    @Test
    void twoModesCannotShareAnId() {
        catalog.register(new ModeSlot("duel", 1, true));

        assertThrows(IllegalStateException.class,
                () -> catalog.register(new ModeSlot("duel", 9, true)));
    }

    @Test
    void aModeIsEnabledOrNot() {
        registerAll();

        assertTrue(catalog.isEnabled("duel"));
        catalog.setEnabled("duel", false);
        assertFalse(catalog.isEnabled("duel"));
        assertFalse(catalog.isEnabled("never-installed"));
    }
}

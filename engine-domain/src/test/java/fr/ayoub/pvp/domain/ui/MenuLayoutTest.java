package fr.ayoub.pvp.domain.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLayoutTest {

    // --- size ------------------------------------------------------------------

    @Test
    void aChestRowIsNineSlots() {
        assertEquals(27, MenuLayout.borderless(3).size());
        assertEquals(54, MenuLayout.borderless(6).size());
    }

    @Test
    void aChestHasBetweenOneAndSixRows() {
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.borderless(0));
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.borderless(7));
    }

    // --- content slots ---------------------------------------------------------

    @Test
    void aBorderlessMenuUsesEverySlot() {
        assertEquals(27, MenuLayout.borderless(3).itemsPerPage());
    }

    @Test
    void aBorderedMenuOnlyUsesTheInnerArea() {
        // 6 rows -> inner rows 1..4 (4) x inner columns 1..7 (7) = 28
        MenuLayout layout = MenuLayout.bordered(6);

        assertEquals(28, layout.itemsPerPage());
        assertEquals(10, layout.slotAt(0), "the first inner slot of a 6-row chest is 10");
    }

    // --- pagination ------------------------------------------------------------

    @Test
    void thereIsAlwaysAtLeastOnePageEvenWhenEmpty() {
        assertEquals(1, MenuLayout.bordered(6).pageCount(0));
    }

    @Test
    void aFullPageIsStillOnePage() {
        assertEquals(1, MenuLayout.bordered(6).pageCount(28));
    }

    @Test
    void oneItemTooManyStartsASecondPage() {
        assertEquals(2, MenuLayout.bordered(6).pageCount(29));
    }

    @Test
    void pagesSliceTheItemsInOrder() {
        MenuLayout layout = MenuLayout.borderless(1); // 9 per page
        List<String> items = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");

        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), layout.pageItems(items, 0));
        assertEquals(List.of("j", "k"), layout.pageItems(items, 1), "the last page may be partial");
    }

    @Test
    void askingForAPageThatDoesNotExistGivesNothing() {
        MenuLayout layout = MenuLayout.borderless(1);

        assertTrue(layout.pageItems(List.of("a"), 5).isEmpty());
        assertTrue(layout.pageItems(List.of(), 0).isEmpty());
    }

    @Test
    void aNegativePageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.borderless(1).pageItems(List.of("a"), -1));
    }
}

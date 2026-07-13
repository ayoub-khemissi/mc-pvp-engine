package fr.ayoub.pvp.mode.fortress.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTextTest {

    @Test
    void aShortLineIsLeftAlone() {
        assertEquals(List.of("Put it on"), SignText.wrap("Put it on", 3));
    }

    @Test
    void aLongSentenceIsBrokenOnWords() {
        List<String> lines = SignText.wrap("The enemy digs in and smashes it", 3);

        assertTrue(lines.size() <= 3);
        lines.forEach(line -> assertTrue(line.length() <= SignText.MAX_CHARS,
                "'" + line + "' is " + line.length() + " chars"));
        assertEquals("The enemy digs", lines.getFirst(), "it breaks between words, never inside one");
    }

    @Test
    void textThatCannotFitIsRefusedRatherThanTruncated() {
        // This is the whole point. Minecraft silently CUTS a line that is too wide — the
        // sign then reads "Crystal dies=you" and nobody ever finds out it said "lose".
        assertThrows(IllegalArgumentException.class,
                () -> SignText.wrap("Crystal dies and then you immediately lose the match", 2));
    }

    @Test
    void aSingleWordTooLongToFitIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SignText.wrap("Supercalifragilistic", 4));
    }

    @Test
    void everyBoardPanelActuallyFitsOnASign() {
        // The regression test for the bug in the screenshot: the last line of the last
        // panel was cut off in game. If anybody rewrites the board and overruns, this fails
        // here rather than in front of a player.
        for (BoardText.Panel panel : BoardText.PANELS) {
            assertTrue(panel.title().length() <= SignText.MAX_CHARS,
                    "title too wide: '" + panel.title() + "'");

            List<String> lines = SignText.wrap(panel.body(), SignText.MAX_LINES - 1);
            lines.forEach(line -> assertTrue(line.length() <= SignText.MAX_CHARS,
                    "line too wide: '" + line + "'"));
        }
    }

    @Test
    void everyButtonLabelFits() {
        for (RoomButton button : RoomButton.values()) {
            assertTrue(button.label().length() <= SignText.MAX_CHARS,
                    "label too wide: '" + button.label() + "'");
            assertTrue(button.hint().length() <= SignText.MAX_CHARS,
                    "hint too wide: '" + button.hint() + "'");
        }
    }
}

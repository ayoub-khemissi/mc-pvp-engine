package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatTest {

    /**
     * A battle royale is one queue and one rating pool — "solo" — no matter how many actually
     * join. The team count is only the cap; the label is the identity, so an 18-player match and
     * a 24-player match share a rating and a queue rather than splitting into two.
     */
    @Test
    void soloIsOneRatingPoolWhateverTheCap() {
        Format format = Format.solo(24);

        assertEquals("solo", format.id());
        assertEquals(24, format.teams());
        assertEquals(1, format.playersPerTeam());
    }

    @Test
    void aLabelBecomesTheId() {
        Format labelled = new Format(24, 1, "solo");
        assertEquals("solo", labelled.id());
    }

    @Test
    void withoutALabelTheIdIsComputedAsBefore() {
        assertEquals("3v3", new Format(2, 3).id());     // 2 teams of 3
        assertEquals("1v1v1", new Format(3, 1).id());   // 3 teams of 1
    }

    @Test
    void twoFormatsWithDifferentLabelsAreDifferentPools() {
        assertNotEquals(Format.solo(24), new Format(24, 1));
    }

    @Test
    void oneVersusOneIsTwoTeamsOfOne() {
        Format format = Format.parse("1v1");

        assertEquals(2, format.teams());
        assertEquals(1, format.playersPerTeam());
        assertEquals(2, format.totalPlayers());
        assertEquals("1v1", format.id());
    }

    @Test
    void threeVersusThreeIsSixPlayers() {
        assertEquals(6, Format.parse("3v3").totalPlayers());
    }

    @Test
    void moreThanTwoTeamsIsAllowed() {
        Format freeForAll = Format.parse("1v1v1");

        assertEquals(3, freeForAll.teams());
        assertEquals(3, freeForAll.totalPlayers());
    }

    @Test
    void teamsMustBeTheSameSize() {
        assertThrows(IllegalArgumentException.class, () -> Format.parse("2v3"));
    }

    @Test
    void nonsenseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Format.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> Format.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Format.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> Format.parse("0v0"));
    }
}

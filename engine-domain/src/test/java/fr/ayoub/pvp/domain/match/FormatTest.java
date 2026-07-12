package fr.ayoub.pvp.domain.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatTest {

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

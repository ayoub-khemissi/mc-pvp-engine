package fr.ayoub.pvp.domain.queue;

import fr.ayoub.pvp.domain.match.Format;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    private static final long NOW = 1_000_000L;

    /** ±50 to start, +25 for every 5s of waiting, never more than ±500. */
    private final RatingWindow window = new RatingWindow(50, 25, 500);

    private final Matchmaker duel1v1 = new Matchmaker(Format.parse("1v1"), window);
    private final Matchmaker duel2v2 = new Matchmaker(Format.parse("2v2"), window);

    private static Ticket waiting(int rating, long secondsWaited) {
        return new Ticket(UUID.randomUUID(), rating, NOW - secondsWaited * 1000L);
    }

    // --- not enough players ----------------------------------------------------

    @Test
    void anEmptyQueueMatchesNobody() {
        assertTrue(duel1v1.tryForm(List.of(), NOW).isEmpty());
    }

    @Test
    void oneLonelyPlayerWaits() {
        assertTrue(duel1v1.tryForm(List.of(waiting(1000, 0)), NOW).isEmpty());
    }

    @Test
    void threePlayersCannotFillA2v2() {
        List<Ticket> queue = List.of(waiting(1000, 0), waiting(1000, 0), waiting(1000, 0));
        assertTrue(duel2v2.tryForm(queue, NOW).isEmpty());
    }

    // --- the rating window -----------------------------------------------------

    @Test
    void twoPlayersOfSimilarRatingAreMatched() {
        List<Ticket> queue = List.of(waiting(1000, 0), waiting(1030, 0));

        Optional<Pairing> pairing = duel1v1.tryForm(queue, NOW);

        assertTrue(pairing.isPresent());
        assertEquals(2, pairing.get().teams().size());
        assertEquals(1, pairing.get().teams().get(0).size());
    }

    @Test
    void playersTooFarApartAreNotMatchedAtFirst() {
        // 300 apart, the window starts at 50
        List<Ticket> queue = List.of(waiting(1000, 0), waiting(1300, 0));

        assertTrue(duel1v1.tryForm(queue, NOW).isEmpty());
    }

    @Test
    void theWindowWidensWithWaiting() {
        // after 60s: 50 + (60/5)*25 = 350 -> a 300 gap now fits
        List<Ticket> queue = List.of(waiting(1000, 60), waiting(1300, 0));

        assertTrue(duel1v1.tryForm(queue, NOW).isPresent(),
                "a player who waited long enough should be matched with a further-away opponent");
    }

    @Test
    void theWindowStopsGrowingAtItsMaximum() {
        assertEquals(50, window.widthAt(0));
        assertEquals(350, window.widthAt(60_000));
        assertEquals(500, window.widthAt(10_000_000), "capped");
    }

    // --- fairness --------------------------------------------------------------

    @Test
    void theLongestWaitingPlayerIsAlwaysServedFirst() {
        Ticket veteranOfTheQueue = waiting(1000, 120);
        Ticket fresh1 = waiting(1005, 0);
        Ticket fresh2 = waiting(1010, 0);

        Pairing pairing = duel1v1.tryForm(List.of(fresh1, fresh2, veteranOfTheQueue), NOW).orElseThrow();

        List<Ticket> picked = pairing.allTickets();
        assertTrue(picked.contains(veteranOfTheQueue),
                "the player who has been waiting the longest must be in the match");
    }

    @Test
    void theClosestOpponentIsPreferred() {
        Ticket me = waiting(1000, 30);
        Ticket close = waiting(1010, 0);
        Ticket far = waiting(1090, 0);

        Pairing pairing = duel1v1.tryForm(List.of(me, far, close), NOW).orElseThrow();

        assertTrue(pairing.allTickets().contains(close));
        assertTrue(!pairing.allTickets().contains(far));
    }

    // --- team balancing --------------------------------------------------------

    @Test
    void teamsAreBalancedBySnakeDraft() {
        // 100 / 90 / 80 / 70  ->  teams of 170 each, not 190 vs 150
        List<Ticket> queue = new ArrayList<>(List.of(
                waiting(1100, 10), waiting(1090, 0), waiting(1080, 0), waiting(1070, 0)));

        Pairing pairing = new Matchmaker(Format.parse("2v2"), new RatingWindow(500, 0, 500))
                .tryForm(queue, NOW).orElseThrow();

        int teamA = pairing.teams().get(0).stream().mapToInt(Ticket::rating).sum();
        int teamB = pairing.teams().get(1).stream().mapToInt(Ticket::rating).sum();

        assertEquals(teamA, teamB, "a snake draft must produce two equal teams here");
    }

    @Test
    void aPairingHasExactlyTheRightNumberOfPlayers() {
        List<Ticket> queue = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            queue.add(waiting(1000, 0));
        }

        Pairing pairing = duel2v2.tryForm(queue, NOW).orElseThrow();

        assertEquals(2, pairing.teams().size());
        assertEquals(2, pairing.teams().get(0).size());
        assertEquals(2, pairing.teams().get(1).size());
        assertEquals(4, pairing.allTickets().size());
    }
}

package fr.ayoub.pvp.domain.queue;

import fr.ayoub.pvp.domain.match.Format;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    private static final long NOW = 1_000_000L;

    /** ±50 to start, +25 for every 5s of waiting, never more than ±500. */
    private final RatingWindow window = new RatingWindow(50, 25, 500);

    private final Matchmaker duel1v1 = new Matchmaker(Format.parse("1v1"), window);
    private final Matchmaker duel2v2 = new Matchmaker(Format.parse("2v2"), window);

    /** A lone player. */
    private static Ticket solo(int rating, long secondsWaited) {
        return Ticket.solo(UUID.randomUUID(), rating, NOW - secondsWaited * 1000L);
    }

    /** A group of friends queueing together. */
    private static Ticket party(int size, int rating, long secondsWaited) {
        List<UUID> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(UUID.randomUUID());
        }
        return new Ticket(UUID.randomUUID(), members, rating, NOW - secondsWaited * 1000L);
    }

    // ==========================================================================
    @Nested
    class Solos {

        @Test
        void anEmptyQueueMatchesNobody() {
            assertTrue(duel1v1.tryForm(List.of(), NOW).isEmpty());
        }

        @Test
        void oneLonelyPlayerWaits() {
            assertTrue(duel1v1.tryForm(List.of(solo(1000, 0)), NOW).isEmpty());
        }

        @Test
        void threePlayersCannotFillA2v2() {
            assertTrue(duel2v2.tryForm(List.of(solo(1000, 0), solo(1000, 0), solo(1000, 0)), NOW).isEmpty());
        }

        @Test
        void twoPlayersOfSimilarRatingAreMatched() {
            Optional<Pairing> pairing = duel1v1.tryForm(List.of(solo(1000, 0), solo(1030, 0)), NOW);

            assertTrue(pairing.isPresent());
            assertEquals(2, pairing.get().teams().size());
        }

        @Test
        void playersTooFarApartAreNotMatchedAtFirst() {
            assertTrue(duel1v1.tryForm(List.of(solo(1000, 0), solo(1300, 0)), NOW).isEmpty());
        }

        @Test
        void theWindowWidensWithWaiting() {
            // after 60s: 50 + 12*25 = 350 -> a 300 gap now fits
            assertTrue(duel1v1.tryForm(List.of(solo(1000, 60), solo(1300, 0)), NOW).isPresent());
        }

        @Test
        void theWindowStopsGrowingAtItsMaximum() {
            assertEquals(50, window.widthAt(0));
            assertEquals(350, window.widthAt(60_000));
            assertEquals(500, window.widthAt(10_000_000));
        }

        @Test
        void theLongestWaitingPlayerIsAlwaysServedFirst() {
            Ticket veteran = solo(1000, 120);
            Pairing pairing = duel1v1.tryForm(List.of(solo(1005, 0), solo(1010, 0), veteran), NOW).orElseThrow();

            assertTrue(pairing.allTickets().contains(veteran));
        }

        @Test
        void theClosestOpponentIsPreferred() {
            Ticket me = solo(1000, 30);
            Ticket close = solo(1010, 0);
            Ticket far = solo(1090, 0);

            Pairing pairing = duel1v1.tryForm(List.of(me, far, close), NOW).orElseThrow();

            assertTrue(pairing.allTickets().contains(close));
            assertFalse(pairing.allTickets().contains(far));
        }

        @Test
        void teamsAreBalancedBySnakeDraft() {
            List<Ticket> queue = List.of(solo(1100, 10), solo(1090, 0), solo(1080, 0), solo(1070, 0));

            Pairing pairing = new Matchmaker(Format.parse("2v2"), new RatingWindow(500, 0, 500))
                    .tryForm(queue, NOW).orElseThrow();

            assertEquals(pairing.averageRating(0), pairing.averageRating(1),
                    "a snake draft must produce two equal teams here");
        }
    }

    // ==========================================================================
    @Nested
    class Parties {

        @Test
        void aPartyIsNeverSplitAcrossTeams() {
            Ticket friends = party(2, 1000, 30);

            Pairing pairing = duel2v2
                    .tryForm(List.of(friends, solo(1010, 0), solo(1005, 0)), NOW)
                    .orElseThrow();

            // whichever team they landed on, both members are on it
            List<List<UUID>> teams = List.of(pairing.teamMembers(0), pairing.teamMembers(1));
            boolean together = teams.stream().anyMatch(team -> team.containsAll(friends.members()));

            assertTrue(together, "friends who queued together must play together");
        }

        @Test
        void twoPartiesFaceEachOther() {
            Ticket red = party(2, 1000, 10);
            Ticket blue = party(2, 1020, 0);

            Pairing pairing = duel2v2.tryForm(List.of(red, blue), NOW).orElseThrow();

            assertEquals(2, pairing.teamMembers(0).size());
            assertEquals(2, pairing.teamMembers(1).size());
            assertTrue(pairing.teamMembers(0).containsAll(red.members())
                            || pairing.teamMembers(1).containsAll(red.members()),
                    "a duo must stay a duo");
        }

        @Test
        void aPartyPlusTwoSolosFillsA2v2() {
            Ticket duo = party(2, 1000, 30);

            Pairing pairing = duel2v2
                    .tryForm(List.of(duo, solo(1000, 0), solo(1000, 0)), NOW)
                    .orElseThrow();

            assertEquals(4, pairing.allPlayers().size());
            assertEquals(2, pairing.teamMembers(0).size());
            assertEquals(2, pairing.teamMembers(1).size());
        }

        @Test
        void aPartyBiggerThanATeamCanNeverBeMatched() {
            // three friends cannot fit in a 2v2 team, so they must never be picked
            Ticket trio = party(3, 1000, 300);

            Optional<Pairing> pairing = duel2v2.tryForm(
                    List.of(trio, solo(1000, 0), solo(1000, 0), solo(1000, 0), solo(1000, 0)), NOW);

            assertTrue(pairing.isPresent(), "the four solos should still get a match");
            assertFalse(pairing.get().allTickets().contains(trio),
                    "a party of 3 cannot be squeezed into a team of 2");
        }

        @Test
        void aDuoCannotQueueForA1v1() {
            assertTrue(duel1v1.tryForm(List.of(party(2, 1000, 0), solo(1000, 0)), NOW).isEmpty());
        }

        @Test
        void everyTeamIsExactlyFull() {
            Pairing pairing = new Matchmaker(Format.parse("3v3"), new RatingWindow(500, 0, 500))
                    .tryForm(List.of(
                            party(2, 1000, 60),
                            solo(1010, 0),
                            party(3, 1005, 0),
                            solo(1020, 0)), NOW)
                    .orElseThrow();

            assertEquals(3, pairing.teamMembers(0).size());
            assertEquals(3, pairing.teamMembers(1).size());
            assertEquals(6, pairing.allPlayers().size());
        }

        @Test
        void aTeamIsRatedPerPlayerNotPerTicket() {
            // a duo at 1000 and a solo at 1300 in the same team of 3
            // -> (1000 + 1000 + 1300) / 3 = 1100, not (1000 + 1300) / 2 = 1150
            Pairing pairing = new Matchmaker(Format.parse("3v3"), new RatingWindow(500, 0, 500))
                    .tryForm(List.of(
                            party(2, 1000, 60),
                            solo(1300, 0),
                            party(3, 1000, 0)), NOW)
                    .orElseThrow();

            int mixedTeam = pairing.teamMembers(0).size() == 3
                    && pairing.teams().get(0).size() == 2 ? 0 : 1;

            assertEquals(1100, pairing.averageRating(mixedTeam));
        }
    }
}

package fr.ayoub.pvp.domain.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A match the matchmaker just built: the teams, already balanced. */
public record Pairing(List<List<Ticket>> teams) {

    public Pairing {
        teams = teams.stream().map(List::copyOf).toList();
    }

    public List<Ticket> allTickets() {
        List<Ticket> all = new ArrayList<>();
        teams.forEach(all::addAll);
        return all;
    }

    /** The players of one team — a party contributes all of its members. */
    public List<UUID> teamMembers(int team) {
        List<UUID> players = new ArrayList<>();
        teams.get(team).forEach(ticket -> players.addAll(ticket.members()));
        return players;
    }

    public List<UUID> allPlayers() {
        List<UUID> players = new ArrayList<>();
        for (int team = 0; team < teams.size(); team++) {
            players.addAll(teamMembers(team));
        }
        return players;
    }

    /**
     * The average rating of a team — what the Elo maths compares.
     *
     * Averaged over <b>players</b>, not tickets: a duo counts twice. Otherwise a team of
     * "one duo at 1000 + one solo at 1300" would be rated 1150 instead of its true 1100,
     * and every party would distort its own matchmaking.
     */
    public int averageRating(int team) {
        return (int) Math.round(weightedAverage(teams.get(team)));
    }

    static double weightedAverage(List<Ticket> tickets) {
        int players = tickets.stream().mapToInt(Ticket::size).sum();
        if (players == 0) {
            return 0;
        }
        int total = tickets.stream().mapToInt(ticket -> ticket.rating() * ticket.size()).sum();
        return (double) total / players;
    }
}

package fr.ayoub.pvp.domain.queue;

import java.util.ArrayList;
import java.util.List;

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

    /** The average rating of a team — what the Elo maths compares. */
    public int averageRating(int team) {
        return (int) Math.round(teams.get(team).stream()
                .mapToInt(Ticket::rating)
                .average()
                .orElse(0));
    }
}

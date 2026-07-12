package fr.ayoub.pvp.domain.queue;

import fr.ayoub.pvp.domain.match.Format;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a queue into a match.
 *
 * The rule, in one sentence: <b>serve whoever has waited longest, give them the
 * closest-rated opponents, and only accept if the spread fits their (widening) window.</b>
 *
 * Serving the longest-waiting player first is what stops someone from being starved
 * while players around them keep getting matched.
 *
 * Pure — no Bukkit, no clock (the time is passed in), so it is fully unit-tested.
 */
public final class Matchmaker {

    private final Format format;
    private final RatingWindow window;

    public Matchmaker(Format format, RatingWindow window) {
        this.format = Objects.requireNonNull(format, "format");
        this.window = Objects.requireNonNull(window, "window");
    }

    public Format format() {
        return format;
    }

    public Optional<Pairing> tryForm(List<Ticket> queue, long nowMillis) {
        int needed = format.totalPlayers();
        if (queue.size() < needed) {
            return Optional.empty();
        }

        Ticket oldest = queue.stream()
                .min(Comparator.comparingLong(Ticket::joinedAtMillis))
                .orElseThrow();

        // The people closest in rating to the one who has waited longest.
        List<Ticket> group = new ArrayList<>();
        group.add(oldest);
        queue.stream()
                .filter(ticket -> !ticket.equals(oldest))
                .sorted(Comparator.comparingInt(ticket -> Math.abs(ticket.rating() - oldest.rating())))
                .limit(needed - 1L)
                .forEach(group::add);

        int allowed = window.widthAt(oldest.waitedMillis(nowMillis));
        if (spread(group) > allowed) {
            return Optional.empty();
        }

        return Optional.of(new Pairing(balance(group)));
    }

    private static int spread(List<Ticket> group) {
        int min = group.stream().mapToInt(Ticket::rating).min().orElse(0);
        int max = group.stream().mapToInt(Ticket::rating).max().orElse(0);
        return max - min;
    }

    /**
     * Snake draft: strongest to team A, next to team B, next to B, next to A…
     * With 1100/1090/1080/1070 that gives 2170 vs 2170 instead of 2190 vs 2150.
     */
    private List<List<Ticket>> balance(List<Ticket> group) {
        List<Ticket> byStrength = new ArrayList<>(group);
        byStrength.sort(Comparator.comparingInt(Ticket::rating).reversed());

        List<List<Ticket>> teams = new ArrayList<>();
        for (int team = 0; team < format.teams(); team++) {
            teams.add(new ArrayList<>());
        }

        for (int i = 0; i < byStrength.size(); i++) {
            int round = i / format.teams();
            int position = i % format.teams();
            int team = (round % 2 == 0) ? position : format.teams() - 1 - position;
            teams.get(team).add(byStrength.get(i));
        }
        return teams;
    }
}

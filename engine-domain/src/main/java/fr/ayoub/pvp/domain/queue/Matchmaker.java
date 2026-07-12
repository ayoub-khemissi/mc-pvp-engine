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
 * Serving the longest-waiting ticket first is what stops someone from being starved while
 * players around them keep getting matched.
 *
 * Because a ticket is a <b>group</b>, filling a match is no longer "take N players": the
 * sizes must add up <i>exactly</i>, per team. A 2v2 can be built from 2+2, or 2+1+1, or
 * 1+1+1+1 — but never from a party of 3. So this is done in two steps:
 *
 * <ol>
 *   <li><b>Select</b> the tickets: the closest ratings whose sizes can fill every team
 *       exactly (a small backtracking search — the numbers here are tiny).</li>
 *   <li><b>Split</b> them into teams: of all the legal splits, keep the one whose team
 *       averages are the closest together.</li>
 * </ol>
 *
 * Pure — no Bukkit, no clock (the time is passed in), so it is fully unit-tested.
 */
public final class Matchmaker {

    /**
     * How many other tickets we are willing to consider around the longest-waiting one.
     * The search is exponential in this number, and looking at the 16 closest-rated
     * candidates is already far more than a real queue ever needs.
     */
    private static final int CANDIDATE_POOL = 16;

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

        // A party bigger than a team can never play this format — ignore it entirely,
        // rather than let it block everyone behind it in the queue.
        List<Ticket> eligible = queue.stream()
                .filter(ticket -> ticket.size() <= format.playersPerTeam())
                .toList();

        if (players(eligible) < needed) {
            return Optional.empty();
        }

        Ticket oldest = eligible.stream()
                .min(Comparator.comparingLong(Ticket::joinedAtMillis))
                .orElseThrow();

        int allowed = window.widthAt(oldest.waitedMillis(nowMillis));

        List<Ticket> candidates = eligible.stream()
                .filter(ticket -> ticket != oldest)
                .filter(ticket -> Math.abs(ticket.rating() - oldest.rating()) <= allowed)
                .sorted(Comparator.comparingInt(ticket -> Math.abs(ticket.rating() - oldest.rating())))
                .limit(CANDIDATE_POOL)
                .toList();

        List<Ticket> selected = new ArrayList<>();
        selected.add(oldest);

        return select(selected, candidates, 0, needed, allowed)
                ? bestSplit(selected).map(Pairing::new)
                : Optional.empty();
    }

    private static int players(List<Ticket> tickets) {
        return tickets.stream().mapToInt(Ticket::size).sum();
    }

    // --- step 1: who plays -----------------------------------------------------------

    /**
     * Depth-first, candidates in closest-rating-first order, so the first complete
     * selection we find is also the tightest one.
     */
    private boolean select(List<Ticket> selected, List<Ticket> candidates, int index, int needed, int allowed) {
        int taken = players(selected);
        if (taken == needed) {
            return bestSplit(selected).isPresent();   // do the sizes actually fit the teams?
        }
        if (taken > needed || index == candidates.size()) {
            return false;
        }

        Ticket candidate = candidates.get(index);
        if (taken + candidate.size() <= needed && fitsWindow(selected, candidate, allowed)) {
            selected.add(candidate);
            if (select(selected, candidates, index + 1, needed, allowed)) {
                return true;
            }
            selected.removeLast();
        }
        return select(selected, candidates, index + 1, needed, allowed);   // skip this one
    }

    /** Would adding this ticket stretch the group beyond the longest waiter's window? */
    private static boolean fitsWindow(List<Ticket> selected, Ticket candidate, int allowed) {
        int min = candidate.rating();
        int max = candidate.rating();
        for (Ticket ticket : selected) {
            min = Math.min(min, ticket.rating());
            max = Math.max(max, ticket.rating());
        }
        return max - min <= allowed;
    }

    // --- step 2: who plays with whom -------------------------------------------------

    /**
     * Every legal way to split the selection into full teams, keeping the fairest.
     *
     * "Fairest" = the smallest gap between the strongest and the weakest team average.
     * For four solos this reproduces exactly the old snake draft (1100/1070 vs 1090/1080),
     * and it keeps working when the sizes are uneven.
     *
     * Empty if the sizes cannot fill the teams at all (a 3-party in a 2v2, say).
     */
    private Optional<List<List<Ticket>>> bestSplit(List<Ticket> selected) {
        // Biggest groups first: they are the constrained ones, and placing them early
        // prunes most of the dead ends immediately.
        List<Ticket> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparingInt(Ticket::size).reversed()
                .thenComparing(Comparator.comparingInt(Ticket::rating).reversed()));

        List<List<Ticket>> teams = new ArrayList<>();
        for (int team = 0; team < format.teams(); team++) {
            teams.add(new ArrayList<>());
        }

        Best best = new Best();
        split(ordered, 0, teams, best);
        return Optional.ofNullable(best.teams);
    }

    /** Mutable "best so far" — a record would need an extra allocation per improvement. */
    private static final class Best {
        private List<List<Ticket>> teams;
        private double imbalance = Double.MAX_VALUE;
    }

    private void split(List<Ticket> ordered, int index, List<List<Ticket>> teams, Best best) {
        if (index == ordered.size()) {
            double imbalance = imbalance(teams);
            if (imbalance < best.imbalance) {
                best.imbalance = imbalance;
                best.teams = teams.stream().map(List::copyOf).toList();
            }
            return;
        }

        Ticket ticket = ordered.get(index);
        boolean firstEmptySeen = false;

        for (List<Ticket> team : teams) {
            if (players(team) + ticket.size() > format.playersPerTeam()) {
                continue;
            }
            // Two empty teams are interchangeable — trying both only doubles the work.
            if (team.isEmpty()) {
                if (firstEmptySeen) {
                    continue;
                }
                firstEmptySeen = true;
            }

            team.add(ticket);
            split(ordered, index + 1, teams, best);
            team.removeLast();
        }
    }

    /** The gap between the strongest and the weakest team. Zero is a perfect match. */
    private static double imbalance(List<List<Ticket>> teams) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (List<Ticket> team : teams) {
            double average = Pairing.weightedAverage(team);
            min = Math.min(min, average);
            max = Math.max(max, average);
        }
        return max - min;
    }
}

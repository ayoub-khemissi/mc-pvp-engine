package fr.ayoub.pvp.domain.fortress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One team choosing which of its fortresses to play.
 *
 * The candidates are the team's fortresses, side by side on the voting plain, numbered
 * <b>1, 2, 3 from left to right</b> — and ordered by rating, so number 1 is always the
 * best-rated player's fortress. That ordering is not cosmetic: it is what settles a tie.
 *
 * <p>A 1–1 split in a 2v2 is not an edge case, it happens constantly. Rather than a coin
 * toss, <b>the tie goes to the lowest-numbered candidate</b> — the best-rated player's
 * fortress. Same rule when nobody votes at all. It is deterministic, it never stalls the
 * match, and it keeps the spirit of "the leader decides" when there is no leader.
 *
 * Pure — no Bukkit, no clock. The 30-second deadline is the engine's problem.
 */
public final class TeamVote {

    private final List<Candidate> candidates;
    private final Map<UUID, Integer> ballots = new HashMap<>();

    /** @param candidates ordered: best-rated player's fortress first. At least one. */
    public TeamVote(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("a vote needs at least one fortress to choose from");
        }
        this.candidates = List.copyOf(candidates);
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    /** @param number 1-based, as shown in front of the fortress */
    public Candidate candidate(int number) {
        return candidates.get(number - 1);
    }

    /** A vote for a number nobody is standing on is simply not a vote. */
    public void cast(UUID voter, int number) {
        if (number < 1 || number > candidates.size()) {
            return;
        }
        ballots.put(voter, number);
    }

    public boolean hasVoted(UUID player) {
        return ballots.containsKey(player);
    }

    public int votesCast() {
        return ballots.size();
    }

    public int tally(int number) {
        return (int) ballots.values().stream().filter(vote -> vote == number).count();
    }

    /** Everyone in the team has voted, so we do not have to wait out the clock. */
    public boolean ready(int teamSize) {
        return ballots.size() >= teamSize;
    }

    /**
     * The fortress this team will play.
     *
     * Most votes wins. Ties — and a team that voted for nothing at all — go to candidate 1,
     * the best-rated player's fortress.
     */
    public Candidate result() {
        int best = 1;
        int bestTally = tally(1);

        for (int number = 2; number <= candidates.size(); number++) {
            if (tally(number) > bestTally) {   // strictly greater: a tie never displaces
                best = number;
                bestTally = tally(number);
            }
        }
        return candidate(best);
    }
}

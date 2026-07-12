package fr.ayoub.pvp.storage;

import java.util.UUID;

/** One line of a leaderboard. */
public record LeaderboardEntry(UUID uuid, String username, int rating, int games, int wins, int losses) {
}

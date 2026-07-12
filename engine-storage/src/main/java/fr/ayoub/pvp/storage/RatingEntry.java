package fr.ayoub.pvp.storage;

/** One of a player's ratings: "duel 1v1 → 1200". */
public record RatingEntry(String modeId, String format, RatingRow row) {
}

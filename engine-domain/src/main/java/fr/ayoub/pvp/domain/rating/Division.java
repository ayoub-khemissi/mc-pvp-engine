package fr.ayoub.pvp.domain.rating;

/** A rank name for a rating range: Bronze, Silver, Gold… */
public record Division(String id, String display, int minRating) {
}

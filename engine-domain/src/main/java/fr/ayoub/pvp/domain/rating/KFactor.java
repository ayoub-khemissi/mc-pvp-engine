package fr.ayoub.pvp.domain.rating;

/**
 * How strongly a single match moves a player's rating.
 * A high K makes the rating move fast (good for new players),
 * a low K keeps established ratings stable.
 */
@FunctionalInterface
public interface KFactor {

    int forGames(int gamesPlayed);

    /** Placement games converge fast, veterans are stable. */
    static KFactor standard() {
        return gamesPlayed -> {
            if (gamesPlayed < 10) return 40;   // placement
            if (gamesPlayed < 30) return 20;   // still settling
            return 10;                         // established
        };
    }

    static KFactor constant(int k) {
        return gamesPlayed -> k;
    }
}

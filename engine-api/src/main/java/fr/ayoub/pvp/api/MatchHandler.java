package fr.ayoub.pvp.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The behaviour of one match of a game mode.
 *
 * Everything has a default, so a plain PvP mode only has to say what kit to give.
 * The engine already handles: teleporting, freezing, the countdown, deaths,
 * "last team standing", the titles, the rating and the cleanup.
 */
public interface MatchHandler {

    /** Give the player their equipment. The only method a vanilla duel needs. */
    void giveKit(MatchContext context, Player player, int team);

    /** Teleported in, frozen, kit given — just before the countdown. */
    default void onPrepare(MatchContext context) {
    }

    /** FIGHT. */
    default void onStart(MatchContext context) {
    }

    default void onPlayerDeath(MatchContext context, Player victim, @Nullable Player killer) {
    }

    /**
     * Decide the winner yourself.
     * Return {@code null} to let the engine apply the default: last team standing.
     */
    default @Nullable MatchOutcome checkWinCondition(MatchContext context) {
        return null;
    }

    default void onEnd(MatchContext context, MatchOutcome outcome) {
    }
}

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

    /**
     * The same, for a mode that has something to <b>load</b> before the match can start.
     *
     * Fortress has to fetch two fortresses out of the database and paste them onto the map.
     * That cannot happen on the main thread, and it cannot happen after the countdown has
     * started either — a fortress that appears around a player who is already fighting is
     * worse than no fortress at all.
     *
     * So the engine waits: call {@code ready} when you are done, and the countdown begins
     * then. Do not call it twice, and do not forget to call it — the engine gives you a few
     * seconds before it gives up and aborts the match, which is the honest outcome when a
     * mode cannot set its own game up.
     */
    default void onPrepare(MatchContext context, Runnable ready) {
        onPrepare(context);
        ready.run();
    }

    /** FIGHT. Called at the start of <b>every</b> round. */
    default void onStart(MatchContext context) {
    }

    /**
     * A round was won. The next one is about to be set up — unless the series is decided,
     * in which case {@link #onEnd} follows immediately.
     */
    default void onRoundEnd(MatchContext context, int winningTeam) {
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

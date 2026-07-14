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

    /**
     * The clock ran out. Who won?
     *
     * The default is a draw, which is the honest answer when a mode has not said otherwise.
     * Fortress counts kills and hands it to whoever has more — and still returns a draw when
     * they are level, because thirty minutes of a real fight that ended dead even is a
     * result, not a failure.
     */
    default MatchOutcome onTimeUp(MatchContext context) {
        return MatchOutcome.draw();
    }

    /** A player is about to come back. Give them their kit again, if your mode has one. */
    default void onRespawn(MatchContext context, Player player, int team) {
        giveKit(context, player, team);
    }

    /**
     * A player who dropped out has reconnected, and the match is still theirs.
     *
     * <p>They are a <b>new Player object</b>. Everything the mode ever pushed at them is gone
     * with the old connection — a boss bar was shown to a player who no longer exists, and
     * showing it again is the only way to get it back. The engine cannot do it for you: it does
     * not know what your mode put on their screen.
     *
     * <p>Whatever the match already did to them still stands (the engine has put them back on
     * their spawn and called {@link #onRespawn}); this is only about what they can <b>see</b>.
     */
    default void onRejoin(MatchContext context, Player player, int team) {
    }

    default void onEnd(MatchContext context, MatchOutcome outcome) {
    }
}

package fr.ayoub.pvp.api;

import fr.ayoub.pvp.domain.match.Format;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What a game mode is handed for the match it is running.
 *
 * The engine owns the arena, the teams and the lifecycle; the mode only reads this
 * and reacts.
 */
public interface MatchContext {

    UUID id();

    GameModeDefinition mode();

    Format format();

    List<Team> teams();

    Optional<Team> teamOf(Player player);

    /** Everyone still in the fight (not eliminated, still online). */
    List<Player> alivePlayers();

    List<Player> onlinePlayers();

    boolean isAlive(Player player);

    /** Take a player out of the fight. The engine then checks the win condition. */
    void eliminate(Player player);

    /** 1-based: the round being played. */
    int round();

    /** Best-of: how many rounds this match can last. */
    int bestOf();

    /** How many rounds a team has already won. */
    int roundsWon(int team);

    World world();

    Location spawn(int team);

    /** The map this match is on. */
    String arenaId();

    /**
     * A named point the map declares, beyond the team spawns.
     *
     * The engine has no idea what these mean, and that is the point: a map says
     * {@code markers: {fortress-pad-0: {...}}} and Fortress knows where to paste a fortress.
     * Payload will say {@code payload-start}, and the engine will still not care.
     *
     * This is also the vocabulary a designer's map will be imported with — the point of a
     * marker is that it carries a <b>name and a position</b>, which a block on the floor
     * cannot.
     */
    Optional<Location> marker(String name);

    void broadcast(Component message);

    void title(Component title, Component subtitle);

    /**
     * Let a player move, or stop them, during the mode's own setup.
     *
     * The engine freezes everybody as soon as they land in the arena, so nobody wanders off
     * before FIGHT. A mode that runs something <b>before</b> the countdown needs that back:
     * Fortress flies its teams around three fortresses so they can look inside the one they
     * are about to bet a match on, and a frozen player cannot fly.
     *
     * The engine freezes them all again at the start of the countdown regardless, so a mode
     * that forgets to re-freeze cannot break anything.
     */
    void freeze(Player player, boolean frozen);

    /**
     * "I am about to change this block. Remember what it was."
     *
     * The engine already watches everything a <b>player</b> does to a destructible arena, and
     * puts it back when the match ends. It cannot watch what a mode does directly: Fortress
     * pastes eight thousand blocks onto a pad without firing a single event, and those blocks
     * would still be standing when the next match started.
     *
     * Call this before writing, and the block goes back with all the others. Calling it twice
     * on the same block is free — only the first state is kept, which is the one that matters.
     */
    void rememberBlock(Location location);

    /** How many kills a team has. The engine counts them; a mode decides what they are worth. */
    int kills(int team);

    /** Seconds left on the clock, or -1 when the mode set no time limit. */
    int secondsLeft();

    /**
     * End the match, now.
     *
     * A mode with a win condition of its own — a crystal broken, a payload delivered — has to
     * be able to say so. Without this, the engine's "last team standing" is the only way a
     * match can end, and a mode with respawn would run forever.
     *
     * Safe to call twice: the second call does nothing.
     */
    void finish(MatchOutcome outcome);
}

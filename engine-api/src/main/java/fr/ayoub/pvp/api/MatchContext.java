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
}

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

    /** 1-based. */
    int round();

    World world();

    Location spawn(int team);

    void broadcast(Component message);

    void title(Component title, Component subtitle);
}

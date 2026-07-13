package fr.ayoub.pvp.api;

import fr.ayoub.pvp.domain.match.Format;
import org.bukkit.entity.Player;

/**
 * The lobby and the queues, opened to a game mode.
 *
 * A mode with a screen of its own needs exactly three things from the engine, and none of
 * them require the engine to know which mode is asking: put this player in a queue, take
 * them out of it, and send them home. Fortress needs all three — its own menu queues for
 * 1v1/2v2/3v3 and sends people to a build zone they must come back from.
 */
public interface EngineLobby {

    /** Queue this player (and their party) for one of your formats. */
    void queue(Player player, GameModeDefinition mode, Format format);

    void leaveQueue(Player player);

    boolean isQueued(Player player);

    boolean isInMatch(Player player);

    /** Back to the lobby: clean inventory, adventure mode, hotbar. Always safe to call. */
    void sendToLobby(Player player);
}

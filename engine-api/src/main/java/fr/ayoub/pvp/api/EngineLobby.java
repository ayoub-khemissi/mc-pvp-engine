package fr.ayoub.pvp.api;

import fr.ayoub.pvp.domain.match.Format;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The lobby, the queues and the parties, opened to a game mode.
 *
 * A mode with a screen of its own needs a handful of things from the engine, and none of
 * them require the engine to know which mode is asking: put this player in a queue, take
 * them out of it, send them home — and tell me who they are playing with. Fortress needs
 * all of them: its menu queues for 1v1/2v2/3v3, it sends people to a build zone they must
 * come back from, and a builder's friends can come and watch them build.
 */
public interface EngineLobby {

    /** Queue this player (and their party) for one of your formats. */
    void queue(Player player, GameModeDefinition mode, Format format);

    void leaveQueue(Player player);

    boolean isQueued(Player player);

    boolean isInMatch(Player player);

    /** Back to the lobby: clean inventory, adventure mode, hotbar. Always safe to call. */
    void sendToLobby(Player player);

    /**
     * Who this player is grouped with, leader first. Empty if they are not in a party.
     *
     * A mode does not get to change a party — it only gets to ask. That is enough for the
     * things a mode actually wants: letting friends watch each other, showing a teammate's
     * rating, refusing a format the group is too big for.
     */
    List<UUID> partyMembers(Player player);

    /**
     * A player's rating in one of your formats.
     *
     * Reads the database — <b>call it off the main thread</b>. A mode needs it to sort people:
     * Fortress lines three fortresses up best-rated first, and that order is what settles a
     * tied vote. Without it the tie-break would be a coin toss.
     */
    int ratingOf(UUID player, GameModeDefinition mode, Format format);
}

package fr.ayoub.pvp.core.lobby;

import fr.ayoub.pvp.api.EngineLobby;
import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.party.Party;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** The engine's side of {@link EngineLobby}. A thin pass-through — that is the point. */
public final class CoreLobby implements EngineLobby {

    private final PvPEnginePlugin plugin;

    public CoreLobby(PvPEnginePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void queue(Player player, GameModeDefinition mode, Format format) {
        plugin.queue().join(player, mode, format);
    }

    @Override
    public void leaveQueue(Player player) {
        plugin.queue().leave(player);
    }

    @Override
    public boolean isQueued(Player player) {
        return plugin.queue().isQueued(player);
    }

    @Override
    public boolean isInMatch(Player player) {
        return plugin.matches().isInMatch(player);
    }

    @Override
    public void sendToLobby(Player player) {
        plugin.lobby().send(player);
    }

    @Override
    public List<UUID> partyMembers(Player player) {
        return plugin.parties().partyOf(player)
                .map(Party::members)
                .orElse(List.of());
    }
}

package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.PvPEngineApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Where mode plugins announce themselves. Insertion order = order in the Play menu. */
public final class GameModeRegistry implements PvPEngineApi.Registry {

    private final Map<String, GameModeDefinition> modes = new LinkedHashMap<>();
    private final Logger logger;

    public GameModeRegistry(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void register(GameModeDefinition mode) {
        if (modes.containsKey(mode.id())) {
            throw new IllegalStateException("a game mode with id '" + mode.id() + "' is already registered");
        }
        if (mode.formats().isEmpty()) {
            throw new IllegalArgumentException("game mode '" + mode.id() + "' declares no format");
        }
        modes.put(mode.id(), mode);
        logger.info("Game mode registered: " + mode.id() + " " + mode.formats());
    }

    @Override
    public Optional<GameModeDefinition> byId(String id) {
        return Optional.ofNullable(modes.get(id));
    }

    @Override
    public List<GameModeDefinition> all() {
        return new ArrayList<>(modes.values());
    }
}

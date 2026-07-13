package fr.ayoub.pvp.api;

import java.util.List;
import java.util.Optional;

/**
 * How a game-mode plugin reaches the engine.
 *
 * <pre>
 * // plugin.yml:  depend: [PvPEngine]
 * public void onEnable() {
 *     PvPEngineApi.modes().register(new DuelMode());
 * }
 * </pre>
 */
public final class PvPEngineApi {

    private static Registry registry;
    private static EngineStorage storage;

    private PvPEngineApi() {
    }

    /** Called by the engine on enable. Not for mode plugins. */
    public static void init(Registry registryImpl, EngineStorage storageImpl) {
        registry = registryImpl;
        storage = storageImpl;
    }

    public static Registry modes() {
        return require(registry);
    }

    /** The database — for a mode that has things to remember. It owns its own tables. */
    public static EngineStorage storage() {
        return require(storage);
    }

    private static <T> T require(T service) {
        if (service == null) {
            throw new IllegalStateException(
                    "PvP Engine is not enabled yet — add 'depend: [PvPEngine]' to your plugin.yml");
        }
        return service;
    }

    /** Where game modes announce themselves. */
    public interface Registry {

        void register(GameModeDefinition mode);

        Optional<GameModeDefinition> byId(String id);

        List<GameModeDefinition> all();
    }
}

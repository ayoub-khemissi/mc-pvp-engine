package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.domain.mode.ModeCatalog;
import fr.ayoub.pvp.domain.mode.ModeSlot;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Where mode plugins announce themselves.
 *
 * The <b>order and the on/off state</b> live in a {@link ModeCatalog} (pure, unit-tested);
 * this class only holds the Bukkit-side definitions and asks the catalog what to show.
 *
 * A mode declares its own rank ({@link GameModeDefinition#order()}), and the server owner
 * can override both the rank and the on/off state in {@code config.yml}:
 *
 * <pre>
 * modes:
 *   duel:      {enabled: true,  order: 1}
 *   fortress:  {enabled: false}          # installed, but not in the menu
 * </pre>
 */
public final class GameModeRegistry implements PvPEngineApi.Registry {

    private final Map<String, GameModeDefinition> definitions = new HashMap<>();
    private final ModeCatalog catalog = new ModeCatalog();

    private final ConfigurationSection config;
    private final Logger logger;

    /** @param config the {@code modes} section of config.yml, or null if there is none */
    public GameModeRegistry(Logger logger, ConfigurationSection config) {
        this.logger = logger;
        this.config = config;
    }

    @Override
    public void register(GameModeDefinition mode) {
        if (definitions.containsKey(mode.id())) {
            throw new IllegalStateException("a game mode with id '" + mode.id() + "' is already registered");
        }
        if (mode.formats().isEmpty()) {
            throw new IllegalArgumentException("game mode '" + mode.id() + "' declares no format");
        }

        int order = configInt(mode.id(), "order", mode.order());
        boolean enabled = configBoolean(mode.id(), "enabled", true);

        definitions.put(mode.id(), mode);
        catalog.register(new ModeSlot(mode.id(), order, enabled));

        logger.info("Game mode registered: " + mode.id() + " " + mode.formats()
                + " — rank " + order + (enabled ? "" : ", DISABLED"));
    }

    /** The modes a player can queue for, in menu order. Disabled ones are not here. */
    @Override
    public List<GameModeDefinition> all() {
        return catalog.active().stream()
                .map(slot -> definitions.get(slot.id()))
                .toList();
    }

    /**
     * A mode by id — <b>only if it is enabled</b>.
     *
     * Disabling a mode has to close every door into it, not just hide the icon: a menu left
     * open in someone's inventory, or an old queue ticket, must not be able to start a
     * match in a mode the owner switched off.
     */
    @Override
    public Optional<GameModeDefinition> byId(String id) {
        return catalog.isEnabled(id)
                ? Optional.ofNullable(definitions.get(id))
                : Optional.empty();
    }

    /** Everything installed, on or off, in rank order. For the admin readout. */
    public List<ModeSlot> installed() {
        return catalog.all();
    }

    public boolean isEnabled(String id) {
        return catalog.isEnabled(id);
    }

    /** Turn a mode on or off while the server runs. @return false if no such mode */
    public boolean setEnabled(String id, boolean enabled) {
        if (!catalog.setEnabled(id, enabled)) {
            return false;
        }
        logger.info("Game mode '" + id + "' is now " + (enabled ? "enabled" : "disabled"));
        return true;
    }

    private int configInt(String modeId, String key, int fallback) {
        ConfigurationSection section = config == null ? null : config.getConfigurationSection(modeId);
        return section == null ? fallback : section.getInt(key, fallback);
    }

    private boolean configBoolean(String modeId, String key, boolean fallback) {
        ConfigurationSection section = config == null ? null : config.getConfigurationSection(modeId);
        return section == null ? fallback : section.getBoolean(key, fallback);
    }
}

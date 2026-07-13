package fr.ayoub.pvp.api;

import fr.ayoub.pvp.domain.match.Format;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A game mode.
 *
 * Implement this in your own plugin, then register it:
 *
 * <pre>
 * public void onEnable() {
 *     PvPEngineApi.modes().register(new DuelMode());
 * }
 * </pre>
 *
 * The engine gives you queues, arenas, walls, ELO and the UI for free.
 */
public interface GameModeDefinition {

    /** Stable id, also stored in the database: "duel", "dodgeball"… */
    String id();

    Component displayName();

    /**
     * Where this mode sits in the Play menu: duel asks for 1, fortress for 2, payload for 3.
     *
     * A <b>declared</b> rank, not a list index. Turning duel off does not renumber anybody
     * — fortress simply becomes the first entry left, and duel goes back to the top when it
     * is turned on again. Without this the menu would follow plugin load order, which is
     * alphabetical, and would reshuffle itself the day a jar is renamed.
     *
     * The server owner can override it in config.yml (<code>modes.&lt;id&gt;.order</code>).
     */
    default int order() {
        return 100;
    }

    /** Shown in the "Play" menu. */
    ItemStack icon();

    /** Which formats can be queued: 1v1, 2v2, … */
    List<Format> formats();

    /** Does a win change the players' rating? */
    boolean ranked();

    MatchRules rules();

    /** A fresh handler for each match. */
    MatchHandler createHandler();
}

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

    /**
     * The screen the compass opens for this mode.
     *
     * Return {@code null} — the default — and the engine shows its own: the list of formats,
     * click one, you are queued. That is all Duel will ever need.
     *
     * A mode with more to offer returns its own {@link fr.ayoub.pvp.api.ui.Menu}: Fortress
     * queues <i>and</i> sends you to a build zone, so it puts up a screen the engine could
     * not have guessed. The engine still knows nothing about it — it just opens what it is
     * handed, and {@link PvPEngineApi#lobby()} is how that screen queues people back.
     */
    default ModeScreen screen() {
        return null;
    }

    /** What the compass opens for a mode that has its own screen. */
    @FunctionalInterface
    interface ModeScreen {
        void open(org.bukkit.entity.Player player);
    }

    /** Does a win change the players' rating? */
    boolean ranked();

    /**
     * Refuse to be played on a general-purpose arena.
     *
     * A map that names no mode means "any mode" — a sensible default for a plain fighting
     * island, and wrong the moment a mode turns up that <b>cannot</b> be played on one.
     * Fortress needs two fortress pads, resources and a destructible floor; dropped onto a
     * duel island it produces a match that starts and can never be won.
     *
     * Say true, and the engine will only ever put you on a map whose {@code map.yml} names
     * you — and will tell the player, rather than queue them forever, if there is none.
     */
    default boolean requiresDedicatedMap() {
        return false;
    }

    MatchRules rules();

    /** A fresh handler for each match. */
    MatchHandler createHandler();
}

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

package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Step 1: pick a game mode. Modes come from the plugins that registered them. */
public final class PlayMenu extends Menu {

    private final PvPEnginePlugin plugin;

    public PlayMenu(PvPEnginePlugin plugin) {
        super(Component.text("Play", NamedTextColor.DARK_GREEN), MenuLayout.bordered(4));
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        List<GameModeDefinition> modes = plugin.modes().all();

        if (modes.isEmpty()) {
            set(layout().slotAt(4), Icons.of(Material.BARRIER,
                    Component.text("No game mode installed", NamedTextColor.RED),
                    Component.text("Add a mode plugin (e.g. ModeDuel).", NamedTextColor.GRAY)));
            return;
        }

        List<GameModeDefinition> shown = pageItems(modes);
        for (int i = 0; i < shown.size(); i++) {
            GameModeDefinition mode = shown.get(i);

            set(layout().slotAt(i), mode.icon(), event ->
                    new FormatMenu(plugin, mode, this).open(viewer));
        }

        paginate(viewer, modes.size());
    }
}

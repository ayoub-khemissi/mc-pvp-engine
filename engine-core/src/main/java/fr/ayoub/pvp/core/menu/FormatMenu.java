package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Step 2: pick 1v1 / 2v2 / … and join the queue. */
public final class FormatMenu extends Menu {

    private final PvPEnginePlugin plugin;
    private final GameModeDefinition mode;

    public FormatMenu(PvPEnginePlugin plugin, GameModeDefinition mode) {
        super(mode.displayName(), MenuLayout.bordered(3));
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    protected void build(Player viewer) {
        List<Format> formats = mode.formats();

        for (int i = 0; i < formats.size() && i < layout().itemsPerPage(); i++) {
            Format format = formats.get(i);

            set(layout().slotAt(i), Icons.of(Material.IRON_SWORD,
                            Component.text(format.id(), NamedTextColor.GREEN),
                            Component.text(format.totalPlayers() + " players", NamedTextColor.GRAY),
                            Component.text(mode.ranked() ? "Ranked" : "Casual", NamedTextColor.DARK_GRAY)),
                    event -> plugin.queue().join(viewer, mode, format));
        }
    }
}

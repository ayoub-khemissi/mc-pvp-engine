package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.party.Party;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Step 2: pick 1v1 / 2v2 / … and join the queue. */
public final class FormatMenu extends Menu {

    private final PvPEnginePlugin plugin;
    private final GameModeDefinition mode;

    public FormatMenu(PvPEnginePlugin plugin, GameModeDefinition mode, Menu parent) {
        super(mode.displayName(), MenuLayout.bordered(4), parent);
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    protected void build(Player viewer) {
        List<Format> formats = mode.formats();

        // A party of 3 simply cannot play a 2v2. Say so on the icon, rather than let them
        // queue and wait forever for a match that can never be built.
        int partySize = plugin.parties().partyOf(viewer).map(Party::size).orElse(1);

        List<Format> shown = pageItems(formats);
        for (int i = 0; i < shown.size(); i++) {
            Format format = shown.get(i);
            boolean fits = partySize <= format.playersPerTeam();

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(format.totalPlayers() + " players", NamedTextColor.GRAY));
            lore.add(Component.text(mode.ranked() ? "Ranked" : "Casual", NamedTextColor.DARK_GRAY));

            if (partySize > 1) {
                lore.add(Component.empty());
                lore.add(fits
                        ? Component.text("Your party of " + partySize + " fits", NamedTextColor.GREEN)
                        : Component.text("Your party of " + partySize + " is too big",
                                NamedTextColor.RED));
            }

            set(layout().slotAt(i), Icons.of(
                            fits ? Material.IRON_SWORD : Material.BARRIER,
                            Component.text(format.id(), fits ? NamedTextColor.GREEN : NamedTextColor.RED),
                            lore.toArray(Component[]::new)),
                    event -> plugin.queue().join(viewer, mode, format));
        }

        paginate(viewer, formats.size());
    }
}

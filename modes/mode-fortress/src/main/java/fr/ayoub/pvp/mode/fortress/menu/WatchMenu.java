package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.mode.fortress.build.BuildSession;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** The party members who are building right now. Click a head, you are in their room. */
public final class WatchMenu extends Menu {

    private final BuildZoneService zones;

    public WatchMenu(BuildZoneService zones, Menu parent) {
        super(Component.text("Watch a teammate", NamedTextColor.AQUA),
                MenuLayout.bordered(3), parent);
        this.zones = zones;
    }

    @Override
    protected void build(Player viewer) {
        List<Player> building = zones.buildingPartyMembers(viewer);

        if (building.isEmpty()) {
            set(layout().slotAt(3), Icons.of(Material.BARRIER,
                    Component.text("Nobody in your party is building", NamedTextColor.RED),
                    Component.text("They have to be in a build zone right now.",
                            NamedTextColor.GRAY)));
            return;
        }

        List<Player> shown = pageItems(building);

        for (int i = 0; i < shown.size(); i++) {
            Player builder = shown.get(i);

            String fortress = zones.sessionOf(builder)
                    .map(BuildSession::name)
                    .orElse("a fortress");

            set(layout().slotAt(i), Icons.head(builder,
                            Component.text(builder.getName(), NamedTextColor.WHITE),
                            Component.text("Building " + fortress, NamedTextColor.GRAY),
                            Component.empty(),
                            Component.text("Click to watch", NamedTextColor.GREEN),
                            Component.text("Double-Shift to come back", NamedTextColor.DARK_GRAY)),
                    event -> {
                        viewer.closeInventory();
                        zones.watch(viewer, builder);
                    });
        }

        paginate(viewer, building.size());
    }
}

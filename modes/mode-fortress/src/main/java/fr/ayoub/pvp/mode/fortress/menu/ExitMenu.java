package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * "You have unsaved changes."
 *
 * Leaving must never silently overwrite the slot, and it must never silently throw an
 * hour of building away either. So it asks — once, and only when there is something to
 * lose.
 */
public final class ExitMenu extends Menu {

    private final BuildZoneService zones;

    public ExitMenu(BuildZoneService zones) {
        super(Component.text("Unsaved changes", NamedTextColor.RED), MenuLayout.bordered(3));
        this.zones = zones;
    }

    @Override
    protected void build(Player viewer) {
        set(layout().slotAt(2), Icons.of(Material.LIME_CONCRETE,
                        Component.text("Save and leave", NamedTextColor.GREEN),
                        Component.text("Overwrites the slot you were editing.", NamedTextColor.GRAY)),
                event -> {
                    viewer.closeInventory();
                    zones.exit(viewer, true);
                });

        set(layout().slotAt(4), Icons.of(Material.RED_CONCRETE,
                        Component.text("Leave without saving", NamedTextColor.RED),
                        Component.text("Everything since your last save is lost.", NamedTextColor.GRAY)),
                event -> {
                    viewer.closeInventory();
                    zones.exit(viewer, false);
                });

        set(layout().slotAt(6), Icons.of(Material.ARROW,
                        Component.text("Keep building", NamedTextColor.YELLOW)),
                event -> viewer.closeInventory());
    }
}

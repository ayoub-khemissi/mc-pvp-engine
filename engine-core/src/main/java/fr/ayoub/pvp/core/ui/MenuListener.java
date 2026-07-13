package fr.ayoub.pvp.core.ui;

import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.menu.Menus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Routes clicks in a {@link Menu} to that menu, and blocks item stealing. */
public final class MenuListener implements Listener {

    private final PvPEnginePlugin plugin;

    public MenuListener(PvPEnginePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();

        // The player's own inventory, showing below the open menu. Its hotbar still works
        // as a navigation bar: right-click "Spectate" while the profile is open, and you
        // land on the spectate screen — the same as if no menu had been open at all.
        if (clicked != null && !clicked.equals(event.getView().getTopInventory())) {
            event.setCancelled(true);   // and it is never a way to take the hotbar apart

            if (event.isRightClick()) {
                plugin.hotbar().actionOf(event.getCurrentItem())
                        .ifPresent(action -> Menus.open(plugin, player, action));
            }
            return;
        }

        menu.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}

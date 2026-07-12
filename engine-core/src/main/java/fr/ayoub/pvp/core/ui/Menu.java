package fr.ayoub.pvp.core.ui;

import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu.
 *
 * The engine's whole "no commands" UX is built on this: the player clicks items,
 * never types. Geometry and pagination come from {@link MenuLayout} (unit-tested).
 *
 * Menus are read-only: every click is cancelled, then routed to the slot's handler.
 */
public abstract class Menu implements InventoryHolder {

    private final Component title;
    private final MenuLayout layout;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    private Inventory inventory;

    protected Menu(Component title, MenuLayout layout) {
        this.title = title;
        this.layout = layout;
    }

    /** Fill the menu. Called every time it is opened or refreshed. */
    protected abstract void build(Player viewer);

    public void open(Player viewer) {
        inventory = Bukkit.createInventory(this, layout.size(), title);
        refresh(viewer);
        viewer.openInventory(inventory);
    }

    /** Rebuild the contents in place (e.g. once async data has arrived). */
    public void refresh(Player viewer) {
        if (inventory == null) {
            return;
        }
        handlers.clear();
        inventory.clear();
        build(viewer);
    }

    protected void set(int slot, ItemStack icon) {
        set(slot, icon, null);
    }

    protected void set(int slot, ItemStack icon, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, icon);
        if (onClick != null) {
            handlers.put(slot, onClick);
        }
    }

    protected MenuLayout layout() {
        return layout;
    }

    /** Called by {@link MenuListener}. Never let the player take items out. */
    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

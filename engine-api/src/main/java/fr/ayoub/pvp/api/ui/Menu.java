package fr.ayoub.pvp.api.ui;

import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu.
 *
 * The engine's whole "no commands" UX is built on this: the player clicks items, never
 * types. Geometry and pagination come from {@link MenuLayout} (unit-tested).
 *
 * <p>This lives in the <b>SPI</b>, not in the engine, on purpose: a game mode with a screen
 * of its own — Fortress has a build zone to reach — must be able to build one without the
 * engine knowing that mode exists. Extend it in your mode plugin and the engine's listener
 * routes the clicks for you.
 *
 * Three things every screen gets for free, so no menu has to reinvent them:
 * <ul>
 *   <li><b>Back</b>, top-left, whenever the menu was opened from another one.</li>
 *   <li><b>Paging</b>, bottom corners, as soon as the content does not fit on one page.</li>
 *   <li><b>Read-only</b>: every click is cancelled, then routed to the slot's handler.</li>
 * </ul>
 */
public abstract class Menu implements InventoryHolder {

    /** Top-left. The one place a player looks for the way back. */
    public static final int SLOT_BACK = 0;

    private final Component title;
    private final MenuLayout layout;
    private final Menu parent;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    private Inventory inventory;
    private int page;

    protected Menu(Component title, MenuLayout layout) {
        this(title, layout, null);
    }

    /** @param parent the menu to go back to, or null for a screen opened from the hotbar */
    protected Menu(Component title, MenuLayout layout, Menu parent) {
        this.title = title;
        this.layout = layout;
        this.parent = parent;
    }

    /** Fill the menu. Called every time it is opened, refreshed, or paged. */
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
        drawBack(viewer);   // after build(), so a menu can never paint over its own way out
    }

    private void drawBack(Player viewer) {
        if (parent == null) {
            return;
        }
        set(SLOT_BACK, Icons.of(Material.ARROW,
                        Component.text("Back", NamedTextColor.YELLOW)),
                event -> parent.open(viewer));
    }

    // --- paging ------------------------------------------------------------------

    protected int page() {
        return page;
    }

    /** The slice of {@code items} that belongs on the current page. */
    protected <T> List<T> pageItems(List<T> items) {
        return layout.pageItems(items, page);
    }

    /**
     * Draw the paging controls, if there is more than one page.
     *
     * Call it from {@link #build} with the <b>total</b> number of items — not the number
     * shown. Nothing is drawn when everything fits, so it is safe to call unconditionally.
     */
    protected void paginate(Player viewer, int totalItems) {
        int pages = layout.pageCount(totalItems);

        if (page >= pages) {
            page = pages - 1;   // the list shrank under us (a match ended, a player left)
        }
        if (pages <= 1) {
            return;
        }

        int bottomLeft = layout.size() - 9;

        if (page > 0) {
            set(bottomLeft, Icons.of(Material.ARROW,
                            Component.text("Previous page", NamedTextColor.YELLOW)),
                    event -> turnTo(viewer, page - 1));
        }

        set(bottomLeft + 4, Icons.of(Material.PAPER,
                Component.text("Page " + (page + 1) + " / " + pages, NamedTextColor.GRAY)));

        if (page < pages - 1) {
            set(layout.size() - 1, Icons.of(Material.ARROW,
                            Component.text("Next page", NamedTextColor.YELLOW)),
                    event -> turnTo(viewer, page + 1));
        }
    }

    private void turnTo(Player viewer, int target) {
        page = target;
        onPageChanged(viewer);
    }

    /**
     * The page changed.
     *
     * The default redraws what is already in memory. A menu whose pages live in the
     * database (the leaderboard) overrides this to fetch the new page first.
     */
    protected void onPageChanged(Player viewer) {
        refresh(viewer);
    }

    // --- slots -------------------------------------------------------------------

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

    /**
     * Called by the engine's menu listener — <b>not</b> by a mode.
     *
     * Public only because the listener lives in another package. Never let the player take
     * items out.
     */
    public void handleClick(InventoryClickEvent event) {
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

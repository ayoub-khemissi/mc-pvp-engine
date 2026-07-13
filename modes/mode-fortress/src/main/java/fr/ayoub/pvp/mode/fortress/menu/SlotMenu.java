package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import fr.ayoub.pvp.mode.fortress.storage.SavedFortress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's fortress slots: what is in each, which one is the default, and the way in.
 *
 * Left-click builds. Right-click makes it the default — the one a match falls back on when
 * nobody votes. Shift-right-click empties it.
 */
public final class SlotMenu extends Menu {

    private final FortressConfig config;
    private final BuildZoneService zones;
    private final FortressRepository fortresses;
    private final Plugin plugin;

    private final Map<Integer, SavedFortress> saved = new HashMap<>();
    private boolean loading = true;

    public SlotMenu(FortressConfig config, BuildZoneService zones,
                    FortressRepository fortresses, Menu parent) {
        super(Component.text("My fortresses", NamedTextColor.LIGHT_PURPLE),
                MenuLayout.bordered(3), parent);
        this.config = config;
        this.zones = zones;
        this.fortresses = fortresses;
        this.plugin = zones.plugin();   // never look a plugin up by name — it can return null
    }

    @Override
    public void open(Player viewer) {
        super.open(viewer);
        load(viewer);
    }

    @Override
    protected void build(Player viewer) {
        if (loading) {
            set(layout().slotAt(0), Icons.of(Material.CLOCK,
                    Component.text("Loading…", NamedTextColor.GRAY)));
            return;
        }

        for (int slot = 1; slot <= config.slots(); slot++) {
            SavedFortress fortress = saved.get(slot);
            int at = layout().slotAt(slot - 1);
            int number = slot;

            if (fortress == null) {
                // Not a glass pane: in a chest GUI a pale pane reads as an empty slot, which
                // is precisely the thing it was supposed to distinguish itself from.
                set(at, Icons.of(Material.CRAFTING_TABLE,
                                Component.text("Slot " + slot + " — empty", NamedTextColor.YELLOW),
                                Component.text("Click to start building.", NamedTextColor.GREEN)),
                        event -> enter(viewer, number));
                continue;
            }

            List<Component> lore = new ArrayList<>();
            lore.add(fortress.playable()
                    ? Component.text("Ready to play", NamedTextColor.GREEN)
                    : Component.text("Draft — not playable yet", NamedTextColor.YELLOW));
            lore.add(Component.text(fortress.blockCount() + " blocks · "
                    + fortress.size() + "³", NamedTextColor.GRAY));
            if (fortress.isDefault()) {
                lore.add(Component.text("★ Default", NamedTextColor.GOLD));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Left-click to edit", NamedTextColor.GREEN));
            if (!fortress.isDefault()) {
                lore.add(Component.text("Right-click to make it your default", NamedTextColor.AQUA));
            }
            lore.add(Component.text("Shift + right-click to delete", NamedTextColor.RED));

            set(at, Icons.of(fortress.playable() ? Material.BRICKS : Material.COARSE_DIRT,
                            Component.text(fortress.name(),
                                    fortress.isDefault() ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                            lore.toArray(Component[]::new)),
                    event -> {
                        if (event.isRightClick() && event.isShiftClick()) {
                            delete(viewer, number);
                        } else if (event.isRightClick()) {
                            makeDefault(viewer, number);
                        } else {
                            enter(viewer, number);
                        }
                    });
        }
    }

    private void enter(Player viewer, int slot) {
        viewer.closeInventory();
        zones.enter(viewer, slot);
    }

    private void makeDefault(Player viewer, int slot) {
        PvPEngineApi.storage().async().execute(() -> {
            fortresses.setDefault(viewer.getUniqueId(), slot);
            Bukkit.getScheduler().runTask(plugin, () -> load(viewer));
        });
    }

    private void delete(Player viewer, int slot) {
        PvPEngineApi.storage().async().execute(() -> {
            fortresses.delete(viewer.getUniqueId(), slot);
            Bukkit.getScheduler().runTask(plugin, () -> load(viewer));
        });
    }

    private void load(Player viewer) {
        PvPEngineApi.storage().async().execute(() -> {
            List<SavedFortress> found = fortresses.findAllFor(viewer.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                saved.clear();
                found.forEach(fortress -> saved.put(fortress.slot(), fortress));
                loading = false;
                if (viewer.isOnline()) {
                    refresh(viewer);
                }
            });
        });
    }
}

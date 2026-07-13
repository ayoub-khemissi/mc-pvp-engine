package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
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
 * Every slot is <b>re-checked against the rules as they are now</b>, not against the flag
 * written the day it was saved — and when a fortress fails, the screen says exactly why.
 *
 * Left-click builds. Right-click makes it the default. Shift-right-click empties it.
 */
public final class SlotMenu extends Menu {

    private final FortressConfig config;
    private final BuildZoneService zones;
    private final FortressRepository fortresses;
    private final FortressLibrary library;
    private final Plugin plugin;

    private final Map<Integer, FortressLibrary.Checked> saved = new HashMap<>();
    private boolean loading = true;

    public SlotMenu(FortressConfig config, BuildZoneService zones,
                    FortressRepository fortresses, FortressLibrary library, Menu parent) {
        super(Component.text("My fortresses", NamedTextColor.LIGHT_PURPLE),
                MenuLayout.bordered(3), parent);
        this.config = config;
        this.zones = zones;
        this.fortresses = fortresses;
        this.library = library;
        this.plugin = zones.plugin();
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
            FortressLibrary.Checked checked = saved.get(slot);
            int at = layout().slotAt(slot - 1);
            int number = slot;

            if (checked == null) {
                set(at, Icons.of(Material.CRAFTING_TABLE,
                                Component.text("Slot " + slot + " — empty", NamedTextColor.YELLOW),
                                Component.text("Click to start building.", NamedTextColor.GREEN)),
                        event -> enter(viewer, number));
                continue;
            }

            set(at, icon(checked), event -> {
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

    private org.bukkit.inventory.ItemStack icon(FortressLibrary.Checked checked) {
        boolean isDefault = checked.fortress().isDefault();

        List<Component> lore = new ArrayList<>();

        if (checked.playable()) {
            lore.add(Component.text("Ready to play", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Cannot be played:", NamedTextColor.RED));
            // Say WHY. "Not playable" with no reason is how a player ends up thinking the
            // server is broken, when in fact they are two blocks over the obsidian budget.
            checked.report().problems().forEach(problem ->
                    lore.add(Component.text("  • " + problem, NamedTextColor.GRAY)));
        }

        lore.add(Component.text(checked.fortress().blockCount() + " blocks · "
                + checked.fortress().size() + "³", NamedTextColor.DARK_GRAY));

        if (isDefault) {
            lore.add(Component.text("★ Default", NamedTextColor.GOLD));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Left-click to edit", NamedTextColor.GREEN));
        if (!isDefault) {
            lore.add(Component.text("Right-click to make it your default", NamedTextColor.AQUA));
        }
        lore.add(Component.text("Shift + right-click to delete", NamedTextColor.RED));

        return Icons.of(checked.playable() ? Material.BRICKS : Material.COARSE_DIRT,
                Component.text(checked.name(),
                        isDefault ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                lore.toArray(Component[]::new));
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
            List<FortressLibrary.Checked> found = library.listFor(viewer.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                saved.clear();
                found.forEach(checked -> saved.put(checked.slot(), checked));
                loading = false;
                if (viewer.isOnline()) {
                    refresh(viewer);
                }
            });
        });
    }
}

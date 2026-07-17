package fr.ayoub.pvp.core.admin;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.arena.ArenaLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wand that stamps a floating arena onto the block you point at.
 *
 * <p>{@code /pvpadmin arena stamp <id>} hands you a blaze rod. Right-click a block with it and a
 * clean arena — floor, invisible wall, two facing spawns — appears a few blocks <b>above</b> it, so
 * nothing below is touched (see {@link ArenaStamp}). It is written as a {@code map.yml} and loaded on
 * the spot, ready to queue.
 */
public final class ArenaStamper implements Listener {

    /** How far above the pointed block the platform floats, so the decor is left alone. */
    private static final int DEFAULT_LIFT = 4;
    private static final int DEFAULT_RADIUS = 12;

    private final PvPEnginePlugin plugin;
    private final NamespacedKey wandKey;

    private record Pending(String id, int radius, int lift) {
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    public ArenaStamper(PvPEnginePlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "arenawand");
    }

    /** Give the player a wand primed to stamp arena {@code id}. */
    public void give(Player player, String id, Integer radius, Integer lift) {
        int r = radius != null ? radius : DEFAULT_RADIUS;
        int l = lift != null ? lift : DEFAULT_LIFT;
        pending.put(player.getUniqueId(), new Pending(id, r, l));

        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        wand.editMeta(meta -> {
            meta.displayName(Component.text("Arena Stamp — " + id, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, id);
        });
        player.getInventory().addItem(wand);

        player.sendMessage(Component.text("Point at a block and right-click: the '" + id
                + "' arena (radius " + r + ") appears " + l + " blocks above it.",
                NamedTextColor.GREEN));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.STRING)) {
            return;
        }
        event.setCancelled(true);   // never place the rod

        Player player = event.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) {
            player.sendMessage(Component.text("This wand has expired — /pvpadmin arena stamp <id> again.",
                    NamedTextColor.RED));
            return;
        }

        Block block = event.getClickedBlock();
        int floorY = block.getY() + p.lift();

        try {
            ArenaStamp.stamp(block.getWorld(), block.getX(), floorY, block.getZ(), p.radius(),
                    p.id(), List.of("duel"), new File(plugin.getDataFolder(), "maps"));
        } catch (IOException e) {
            player.sendMessage(Component.text("Could not write the map file: " + e.getMessage(),
                    NamedTextColor.RED));
            return;
        }

        plugin.arenas().load(ArenaLoader.loadAll(plugin));
        plugin.resets().prepare(plugin.arenas().all());

        pending.remove(player.getUniqueId());
        player.getInventory().remove(item);

        player.sendMessage(Component.text("Stamped '" + p.id() + "' at "
                + block.getX() + " " + floorY + " " + block.getZ()
                + " — loaded. Queue a duel to test it.", NamedTextColor.GREEN));
    }
}

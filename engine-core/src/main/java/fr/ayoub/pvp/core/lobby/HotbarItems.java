package fr.ayoub.pvp.core.lobby;

import fr.ayoub.pvp.core.ui.Icons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * The lobby hotbar. This is the "no commands" UX: the player right-clicks an item
 * instead of typing anything.
 *
 * Each item carries its action in its persistent data, so we recognise it even if the
 * player renames or moves it.
 */
public final class HotbarItems {

    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PARTY = "party";
    public static final String ACTION_PROFILE = "profile";
    public static final String ACTION_SPECTATE = "spectate";
    public static final String ACTION_LEAVE_QUEUE = "leave_queue";

    public static final int SLOT_PLAY = 0;
    public static final int SLOT_PARTY = 2;
    public static final int SLOT_PROFILE = 4;
    public static final int SLOT_SPECTATE = 6;
    public static final int SLOT_LEAVE_QUEUE = 8;

    private final NamespacedKey actionKey;

    public HotbarItems(Plugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "hotbar_action");
    }

    public ItemStack play() {
        return tag(Icons.of(Material.COMPASS,
                Component.text("Play", NamedTextColor.GREEN),
                Component.text("Right-click to choose a game mode", NamedTextColor.GRAY)),
                ACTION_PLAY);
    }

    public ItemStack party() {
        return tag(Icons.of(Material.PLAYER_HEAD,
                Component.text("Party", NamedTextColor.LIGHT_PURPLE),
                Component.text("Play with your friends", NamedTextColor.GRAY),
                Component.text("Right-click to invite or answer an invite", NamedTextColor.DARK_GRAY)),
                ACTION_PARTY);
    }

    public ItemStack profile() {
        return tag(Icons.of(Material.BOOK,
                Component.text("Profile", NamedTextColor.AQUA),
                Component.text("Your rank, rating and stats", NamedTextColor.GRAY)),
                ACTION_PROFILE);
    }

    public ItemStack spectate() {
        return tag(Icons.of(Material.ENDER_EYE,
                Component.text("Spectate", NamedTextColor.DARK_AQUA),
                Component.text("Watch a live match", NamedTextColor.GRAY)),
                ACTION_SPECTATE);
    }

    public ItemStack leaveQueue() {
        return tag(Icons.of(Material.BARRIER,
                Component.text("Leave queue", NamedTextColor.RED),
                Component.text("Right-click to stop searching", NamedTextColor.GRAY)),
                ACTION_LEAVE_QUEUE);
    }

    /** The action bound to an item, if it is one of ours. */
    public Optional<String> actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String action = item.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
        return Optional.ofNullable(action);
    }

    private ItemStack tag(ItemStack item, String action) {
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(actionKey, PersistentDataType.STRING, action));
        return item;
    }
}

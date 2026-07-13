package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.lobby.HotbarItems;
import fr.ayoub.pvp.core.ui.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * What each hotbar item opens.
 *
 * One table, used by <b>both</b> ways of reaching a screen: right-clicking the item in the
 * lobby, and right-clicking it in the inventory strip while another menu is already open.
 * Written twice, the two would drift apart the first time a screen is added.
 */
public final class Menus {

    private Menus() {
    }

    public static void open(PvPEnginePlugin plugin, Player player, String action) {
        switch (action) {
            case HotbarItems.ACTION_PLAY -> new PlayMenu(plugin).open(player);
            case HotbarItems.ACTION_PARTY -> new PartyMenu(plugin).open(player);
            case HotbarItems.ACTION_PROFILE -> new ProfileMenu(plugin).open(player);
            case HotbarItems.ACTION_SPECTATE -> new SpectateMenu(plugin).open(player);
            case HotbarItems.ACTION_LEAVE_QUEUE -> leaveQueue(plugin, player);
            default -> {
                // unknown action — ignore
            }
        }
    }

    private static void leaveQueue(PvPEnginePlugin plugin, Player player) {
        plugin.queue().leave(player);
        player.closeInventory();
        player.sendMessage(Component.text("You left the queue.", NamedTextColor.YELLOW));
        Sidebar.update(plugin, player);
    }
}

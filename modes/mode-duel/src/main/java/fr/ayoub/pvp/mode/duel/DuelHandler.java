package fr.ayoub.pvp.mode.duel;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The whole behaviour of a duel.
 *
 * That's it — one method. The engine already does the queue, the arena, the walls,
 * the countdown, the deaths, "last team standing", the titles, the rating and the
 * cleanup.
 */
public final class DuelHandler implements MatchHandler {

    @Override
    public void giveKit(MatchContext context, Player player, int team) {
        PlayerInventory inventory = player.getInventory();

        inventory.setHelmet(new ItemStack(Material.IRON_HELMET));
        inventory.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inventory.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inventory.setBoots(new ItemStack(Material.IRON_BOOTS));

        inventory.setItem(0, new ItemStack(Material.IRON_SWORD));
        inventory.setItem(1, new ItemStack(Material.BOW));
        inventory.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 3));
        inventory.setItem(8, new ItemStack(Material.ARROW, 16));
    }
}

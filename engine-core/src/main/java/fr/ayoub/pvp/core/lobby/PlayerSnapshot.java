package fr.ayoub.pvp.core.lobby;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Everything about a player that a match is allowed to destroy.
 *
 * Taken before they enter an arena and put back afterwards, so a crash, a kick or a
 * restart can never eat someone's inventory.
 */
public record PlayerSnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offhand,
        double health,
        int foodLevel,
        float saturation,
        float exp,
        int level,
        GameMode gameMode,
        Location location,
        boolean allowFlight,
        boolean flying,
        List<PotionEffect> effects) {

    public static PlayerSnapshot of(Player player) {
        PlayerInventory inventory = player.getInventory();

        return new PlayerSnapshot(
                inventory.getContents().clone(),
                inventory.getArmorContents().clone(),
                inventory.getItemInOffHand().clone(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getLevel(),
                player.getGameMode(),
                player.getLocation().clone(),
                player.getAllowFlight(),
                player.isFlying(),
                new ArrayList<>(player.getActivePotionEffects()));
    }

    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();

        inventory.setContents(contents);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(offhand);

        clearEffects(player);
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        player.setHealth(Math.min(health, maxHealth(player)));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExp(exp);
        player.setLevel(level);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.teleport(location);
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }

    private static void clearEffects(Player player) {
        Collection<PotionEffect> active = new ArrayList<>(player.getActivePotionEffects());
        for (PotionEffect effect : active) {
            player.removePotionEffect(effect.getType());
        }
    }
}

package fr.ayoub.pvp.core.lobby;

import fr.ayoub.pvp.core.arena.ArenaService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;

/**
 * Puts a player into the lobby: clean state, lobby hotbar, adventure mode
 * (so nobody can break the lobby).
 *
 * Until matches exist, everybody is in the lobby — {@link #isInLobby(Player)} will
 * later ask the match registry instead.
 */
public final class LobbyService {

    private final Location spawn;
    private final HotbarItems hotbar;
    private final ArenaService arenas;

    public LobbyService(Location spawn, HotbarItems hotbar, ArenaService arenas) {
        this.spawn = spawn;
        this.hotbar = hotbar;
        this.arenas = arenas;
    }

    public void send(Player player) {
        arenas.leave(player);
        reset(player);
        player.teleport(spawn);
        giveItems(player);
    }

    /** In the lobby = not inside an arena. */
    public boolean isInLobby(Player player) {
        return !arenas.isInArena(player);
    }

    public Location spawn() {
        return spawn.clone();
    }

    private void reset(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void giveItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(HotbarItems.SLOT_PLAY, hotbar.play());
        inventory.setItem(HotbarItems.SLOT_PROFILE, hotbar.profile());
        inventory.setHeldItemSlot(HotbarItems.SLOT_PLAY);
    }
}

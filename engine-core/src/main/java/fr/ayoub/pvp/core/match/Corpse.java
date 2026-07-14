package fr.ayoub.pvp.core.match;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * The body a player leaves behind when their connection drops mid-match.
 *
 * <h2>Why</h2>
 *
 * Pulling the plug was the safest move in the game. A player losing a fight closed the client:
 * they vanished in the same tick, the enemy standing over them got nothing, and everything they
 * carried went with them. Dying costs you your inventory and hands the other side a kill.
 * Disconnecting cost neither. <b>The exit was cheaper than the fight</b> — and a rule that makes
 * quitting the strongest play is not a rule, it is a bug.
 *
 * <p>So the body stays, and it <b>is</b> them. Kill it and you have killed them: the kill counts
 * on the scoreboard, their inventory falls at their feet, and they are out of the fight. Log out
 * of a losing fight now and you lose it anyway.
 *
 * <p>The same change is what makes a <b>genuine</b> disconnection survivable. Reconnect while the
 * body still stands and you take it back — the spot, the inventory, and <b>whatever health it has
 * left</b>. If somebody found it and got it down to two hearts before you got back, you come back
 * on two hearts. A crashed client used to cost a player their gear and their position; now it
 * costs them exactly the seconds they were away, and whatever happened to them while they were.
 *
 * <h2>Why it is not an elaborate fake</h2>
 *
 * Because almost none of it is simulated. A {@link Mannequin} extends {@code Avatar} — the same
 * superclass as a real {@code Player} — so it has a player's hitbox and a player's hit points,
 * it wears armour that absorbs and wears down, it takes damage and it dies, all by Minecraft's
 * own rules and none of ours. It does not regenerate (verified: a wounded one stays wounded),
 * which is the one thing that would have made logging out a way to heal.
 *
 * <p>It also carries a {@link ResolvableProfile}: it wears the <b>actual skin</b> of the player
 * who left. The thing standing in the arena is recognisably them — nobody would swing at an
 * armour stand believing they were finishing a fight.
 *
 * <p>All this class does is hand the body their things on the way out, read them back off it on
 * the way in, and tell the match when it dies.
 */
public final class Corpse {

    private final UUID owner;
    private final String name;
    private final Match match;
    private final int team;
    private final Mannequin body;

    /** All 41 slots as they left them. Items in a backpack cannot change; armour can. */
    private final ItemStack[] carried;
    private final int heldSlot;
    private final int food;

    private Corpse(UUID owner, String name, Match match, int team, Mannequin body,
                   ItemStack[] carried, int heldSlot, int food) {
        this.owner = owner;
        this.name = name;
        this.match = match;
        this.team = team;
        this.body = body;
        this.carried = carried;
        this.heldSlot = heldSlot;
        this.food = food;
    }

    /**
     * Take everything off the player and stand it up in the world.
     *
     * <p>Their inventory is <b>cleared</b> on the way out. It is in the body now, and it can only
     * be in one place: leaving a copy on the player would mean reconnecting hands it back while
     * the enemy is looting the original.
     */
    public static Corpse leftBy(Player player, Match match, int team) {
        Location where = player.getLocation();
        PlayerInventory inventory = player.getInventory();

        ItemStack[] carried = inventory.getContents().clone();
        int heldSlot = inventory.getHeldItemSlot();
        int food = player.getFoodLevel();
        double health = player.getHealth();

        Mannequin body = where.getWorld().spawn(where, Mannequin.class, spawned -> {
            spawned.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));

            spawned.customName(Component.text(player.getName(), NamedTextColor.YELLOW));
            spawned.setCustomNameVisible(true);
            spawned.setDescription(Component.text("Disconnected", NamedTextColor.GRAY));

            // Planted. It still takes damage — that was checked, not assumed — but it will not
            // drift, and it will not slide off a ledge and hand somebody a kill they never made.
            spawned.setImmovable(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);

            dress(spawned.getEquipment(), inventory);

            // Their health, not a mannequin's twenty. They must die to exactly the blow that
            // would have killed them, not to a fresh pool they never had.
            if (spawned.getAttribute(Attribute.MAX_HEALTH) != null
                    && player.getAttribute(Attribute.MAX_HEALTH) != null) {
                spawned.getAttribute(Attribute.MAX_HEALTH)
                        .setBaseValue(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            }
            spawned.setHealth(Math.max(0.5, health));
        });

        inventory.clear();
        inventory.setArmorContents(null);

        return new Corpse(player.getUniqueId(), player.getName(), match, team, body,
                carried, heldSlot, food);
    }

    /**
     * What it wears.
     *
     * <p>Nothing drops from the equipment itself — every drop chance is zeroed — because the body
     * spills the <b>whole</b> inventory in one go when it dies, and armour that fell twice would
     * be armour duplicated.
     */
    private static void dress(EntityEquipment worn, PlayerInventory carried) {
        worn.setHelmet(carried.getHelmet());
        worn.setChestplate(carried.getChestplate());
        worn.setLeggings(carried.getLeggings());
        worn.setBoots(carried.getBoots());
        worn.setItemInMainHand(carried.getItemInMainHand());
        worn.setItemInOffHand(carried.getItemInOffHand());

        worn.setHelmetDropChance(0f);
        worn.setChestplateDropChance(0f);
        worn.setLeggingsDropChance(0f);
        worn.setBootsDropChance(0f);
        worn.setItemInMainHandDropChance(0f);
        worn.setItemInOffHandDropChance(0f);
    }

    // --- what can happen to it ----------------------------------------------------------

    /**
     * They came back in time. They take the body back — and they take it back <b>as it is</b>.
     *
     * <p>The health and the armour are read off the <b>body</b>, not off a copy saved when they
     * left. If somebody spent the last twenty seconds hitting it, they inherit that: the hearts
     * it has left are the hearts they get, and the helmet it is wearing is the helmet they get,
     * dents and all. Restoring the snapshot instead would mean logging out was a way to heal, and
     * to repair your armour, which is the exploit this whole class exists to close.
     */
    public void reclaim(Player player) {
        Location at = body.isValid() ? body.getLocation() : player.getLocation();

        player.teleport(at);
        player.setFoodLevel(food);

        // What they had, then what the body has now on top of it.
        player.getInventory().setContents(carried);
        player.getInventory().setHeldItemSlot(heldSlot);

        if (body.isValid()) {
            EntityEquipment worn = body.getEquipment();

            player.getInventory().setHelmet(worn.getHelmet());
            player.getInventory().setChestplate(worn.getChestplate());
            player.getInventory().setLeggings(worn.getLeggings());
            player.getInventory().setBoots(worn.getBoots());
            player.getInventory().setItemInOffHand(worn.getItemInOffHand());
            player.getInventory().setItem(heldSlot, worn.getItemInMainHand());

            double left = Math.min(body.getHealth(),
                    player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setHealth(Math.max(0.5, left));
        }

        remove();
    }

    /** Everything they were carrying, on the ground, where they fell. Exactly like dying. */
    public void spill() {
        Location at = body.isValid() ? body.getLocation() : null;
        if (at == null) {
            return;
        }

        for (ItemStack item : carried) {
            if (item != null && !item.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, item);
            }
        }
        remove();
    }

    public void remove() {
        if (body.isValid()) {
            body.remove();
        }
    }

    public boolean is(Entity entity) {
        return body.getUniqueId().equals(entity.getUniqueId());
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Match match() {
        return match;
    }

    public int team() {
        return team;
    }
}

package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.domain.fortress.CrystalRules;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * The End Crystal, made into something worth defending.
 *
 * Vanilla gives it <b>one hit point</b> and blows it up when it loses it. A fortress whose
 * crystal dies to the first arrow anybody looses at it from across the map is not a fortress
 * — it is a formality. So every scrap of vanilla damage is thrown away here, and the hit is
 * handed to the match, which keeps the health that actually counts.
 *
 * The explosion goes with it. A crystal that detonates would blow a hole in the fortress the
 * attacker is standing in, kill them, and quite possibly take the other crystal's wall with
 * it. Funny exactly once.
 */
public final class CrystalListener implements Listener {

    private final CrystalRegistry registry;

    public CrystalListener(CrystalRegistry registry) {
        this.registry = registry;
    }

    /**
     * Highest priority, and never ignoring cancelled: whatever else the server thinks about
     * this damage, a match crystal does not <b>die</b> of it. What it takes instead is decided
     * by the match, which keeps the health that actually counts.
     *
     * <p>The <b>amount</b>, though, is taken exactly as Minecraft reports it. The game has
     * already done the hard part: the number in this event is the weapon's attack damage,
     * scaled by how far the attack cooldown had recharged, ×1.5 for a critical, plus Sharpness
     * and Strength; an arrow arrives scaled by its draw and by Power. Re-deriving that would be
     * re-implementing the game and getting it subtly wrong, and the player's own damage
     * indicator would then disagree with what the crystal took.
     *
     * <p>This is where the mode used to throw all of it away:
     *
     * <pre>int damage = (int) Math.max(1, Math.round(event.getDamage()));</pre>
     *
     * <p>Rounded to an int and floored to 1 — so a swing mashed at 20% charge and a patient one
     * at 100% both took exactly 1 off a weak weapon, and mashing the button four times a second
     * out-damaged waiting for the cooldown. The floor did not lose precision, it <b>inverted the
     * rule it was meant to enforce</b>.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)
                || !registry.isMatchCrystal(crystal.getUniqueId())) {
            return;
        }

        event.setCancelled(true);   // vanilla would end it here, in one hit

        registry.hit(crystal.getUniqueId(), sourceOf(event), event.getDamage(), attacker(event));
    }

    /**
     * What kind of blow this was. The match decides what each kind is worth — a bow and a
     * stick of TNT do not have to mean against a crystal what they mean against a player.
     *
     * <p>The explosion check comes first on purpose: a stick of TNT arrives as an
     * {@link EntityDamageByEntityEvent} whose damager is the TNT, which would otherwise read
     * as "some entity hit it" and be counted as a melee swing.
     */
    private static CrystalRules.Source sourceOf(EntityDamageEvent event) {
        DamageCause cause = event.getCause();

        if (cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION) {
            return CrystalRules.Source.EXPLOSION;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Projectile) {
                return CrystalRules.Source.PROJECTILE;
            }
            if (byEntity.getDamager() instanceof Player) {
                return CrystalRules.Source.MELEE;
            }
        }
        return CrystalRules.Source.OTHER;
    }

    /**
     * The obsidian under a crystal is part of the crystal.
     *
     * Vanilla would leave it hanging in the air over the hole, which is exactly the wrong
     * answer: an attacker who has tunnelled under a fortress to reach that block has earned
     * the kill. Blowing it up counts too — an explosion breaks the block, and the block is
     * what the crystal is standing on.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        registry.baseBroken(event.getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlast(EntityExplodeEvent event) {
        Player source = event.getEntity() instanceof Player player ? player : null;
        event.blockList().forEach(block -> registry.baseBroken(block, source));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBlast(BlockExplodeEvent event) {
        event.blockList().forEach(block -> registry.baseBroken(block, null));
    }

    /** Nothing a crystal does explodes. Not when it dies, not when it is hit. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal
                && registry.isMatchCrystal(crystal.getUniqueId())) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    /** Who is hitting it: the attacker, or whoever shot the arrow. */
    private static Player attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}

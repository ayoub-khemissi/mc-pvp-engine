package fr.ayoub.pvp.mode.fortress.match;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
     * this damage, a match crystal does not take it. What it takes instead is decided by us.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)
                || !registry.isMatchCrystal(crystal.getUniqueId())) {
            return;
        }

        event.setCancelled(true);   // vanilla would end it here, in one hit

        int damage = (int) Math.max(1, Math.round(event.getDamage()));
        registry.hit(crystal.getUniqueId(), damage, attacker(event));
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

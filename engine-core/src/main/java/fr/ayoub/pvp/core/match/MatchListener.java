package fr.ayoub.pvp.core.match;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Everything that happens to a player while they are in a match. */
public final class MatchListener implements Listener {

    private final MatchService matches;

    public MatchListener(MatchService matches) {
        this.matches = matches;
    }

    /**
     * Fatal damage is intercepted instead of letting the player die.
     *
     * No death screen, no respawn, no dropped items — the player is simply eliminated
     * and put into spectator. That is what makes a duel feel instant.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        matches.matchOf(victim).ifPresent(match -> {
            if (!match.isLive()) {
                event.setCancelled(true);   // nobody can be hurt before FIGHT
                return;
            }
            if (!match.isAlive(victim)) {
                event.setCancelled(true);   // spectators take no damage
                return;
            }

            if (victim.getHealth() - event.getFinalDamage() > 0) {
                return;   // they survive
            }

            event.setCancelled(true);
            matches.handleDeath(victim, killerOf(event));
        });
    }

    /**
     * The backstop for the freeze.
     *
     * The real freeze is {@link Freeze}: the client is told its walk speed is 0, so it does
     * not move at all and nothing has to be corrected. This only catches what the client
     * does not decide — knockback, a piston, an ender pearl still in flight — and should
     * essentially never fire. Cancelling a move is what causes the rubber-band, so it must
     * stay the exception, not the mechanism.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        matches.matchOf(event.getPlayer()).ifPresent(match -> {
            if (match.isLive() || !match.isAlive(event.getPlayer())) {
                return;   // never freeze someone who is watching from spectator mode
            }
            if (changedBlock(event)) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        matches.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (matches.isInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (matches.isInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private static boolean changedBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }

    /** Who gets the kill: the attacker, or whoever shot the arrow. */
    private static Player killerOf(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player attacker) {
            return attacker;
        }
        if (byEntity.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}

package fr.ayoub.pvp.core.match;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    /**
     * The server-side half of "you cannot break the arena".
     *
     * The client-side half is the game mode: a non-building match runs in ADVENTURE, where
     * the client will not even start the break animation. This stays as the authority — a
     * modified client is not bound by its game mode.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        matches.matchOf(event.getPlayer()).ifPresent(match -> {
            if (!match.mode().rules().building()) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        matches.matchOf(event.getPlayer()).ifPresent(match -> {
            if (!match.mode().rules().building()) {
                event.setCancelled(true);
            }
        });
    }

    /**
     * A spectator touches nothing.
     *
     * Minecraft's SPECTATOR mode already forbids all of this, so none of these should ever
     * fire. They are here because "the spectator picked up the arrow that decided the
     * round" is not a bug we are willing to find out about in production — and because an
     * eliminated player is a spectator too, standing in a live arena.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isWatching(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (isWatching(player)) {
            event.setCancelled(true);
            return;
        }
        // No shooting during the countdown, the round break or the victory screen.
        matches.matchOf(player).ifPresent(match -> {
            if (!match.isLive()) {
                event.setCancelled(true);
            }
        });
    }

    /**
     * Nothing may be launched before FIGHT.
     *
     * The client is already stopped by the item cooldown ({@link Freeze}), which is why the
     * bow cannot even be drawn. This is the server having the last word: a modified client
     * is not bound by a cooldown.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        if (isWatching(shooter)) {
            event.setCancelled(true);
            return;
        }
        matches.matchOf(shooter).ifPresent(match -> {
            if (!match.isLive()) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isWatching(event.getPlayer()) || matches.isInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Watching, rather than fighting: a lobby spectator, or a player already eliminated. */
    private boolean isWatching(Player player) {
        if (matches.isSpectating(player)) {
            return true;
        }
        return matches.matchOf(player)
                .map(match -> !match.isAlive(player))
                .orElse(false);
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

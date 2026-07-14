package fr.ayoub.pvp.core.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
     * A blow landed on the body of a disconnected player.
     *
     * <p>It is them, so it plays by their rules: their team-mates cannot hit it, and nobody can
     * hit it before the countdown is over. When it falls, it falls as they would have — see
     * {@link MatchService#handleCorpseDeath}.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCorpseDamage(EntityDamageEvent event) {
        matches.corpseOf(event.getEntity()).ifPresent(corpse -> {
            Match match = corpse.match();

            if (!match.isLive()) {
                event.setCancelled(true);
                return;
            }

            Player attacker = killerOf(event);
            if (attacker == null) {
                return;
            }

            // Friendly fire, on a body. Without this a team could quietly execute the team-mate
            // whose router died, and hand the enemy the kill.
            boolean sameTeam = match.teamOf(attacker)
                    .map(team -> team.index() == corpse.team())
                    .orElse(false);

            if (sameTeam && !match.mode().rules().friendlyFire()) {
                event.setCancelled(true);
            }
        });
    }

    /** The body went down. That is a kill, and that is their inventory on the ground. */
    @EventHandler(ignoreCancelled = true)
    public void onCorpseDeath(EntityDeathEvent event) {
        matches.corpseOf(event.getEntity()).ifPresent(corpse -> {
            event.getDrops().clear();   // the body spills the WHOLE inventory itself, once
            event.setDroppedExp(0);

            matches.handleCorpseDeath(corpse, event.getEntity().getKiller());
        });
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
            if (isFriendlyFire(match, victim, event)) {
                event.setCancelled(true);
                return;
            }

            // THE ATTACKER LOSES THEIRS BY ATTACKING. Before we ask whether the VICTIM is
            // protected — because a protected player who swings has chosen to fight, and the
            // blow they just threw still lands. A shield you can swing from is not a shield.
            Player attacker = killerOf(event);
            if (attacker != null) {
                matches.dropProtection(match, attacker);
            }

            // They only just came back. A respawn mode without this is a spawn camp: the best
            // play is to stand on the enemy's pad and kill them as they materialise.
            if (matches.isProtected(match, victim)) {
                event.setCancelled(true);

                if (attacker != null) {
                    attacker.sendActionBar(Component.text(victim.getName()
                            + " is spawn-protected — " + matches.protectionLeft(match, victim) + "s",
                            NamedTextColor.AQUA));
                }
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
        Player player = event.getPlayer();

        // Never hold a spectator still. They are either dead and watching, or they are being
        // flown somewhere by their mode — Fortress walks its teams through three fortresses
        // before the match — and neither is a player who must not move.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        matches.matchOf(player).ifPresent(match -> {
            if (match.isLive() || !match.isAlive(player)) {
                return;
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

    /**
     * Throwing something on the ground.
     *
     * <p>The dead never do: a spectator has no hands. Beyond that it is the <b>mode's</b> call,
     * and it used to be the engine's — every player in every match was silently stopped, which
     * is right for a duel (five minutes, a kit nobody earned, and a dropped sword is litter) and
     * plainly wrong for a mode where the inventory is the game. In Fortress you mine the blocks,
     * you loot the chests, and handing your teammate the last stack of obsidian while they hold
     * the gate is a <b>move</b>. Cancelling it was not a safety net, it was a missing feature.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (isWatching(player)) {
            event.setCancelled(true);
            return;
        }

        matches.matchOf(player).ifPresent(match -> {
            if (!match.isLive() || !match.mode().rules().dropItems()) {
                event.setCancelled(true);   // frozen in the countdown, or a kit mode
            }
        });
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

    /**
     * A blow one teammate landed on another.
     *
     * <p>The engine stops it, not the mode: the engine is what holds the teams, and a mode that
     * cancelled its own teammates' blows would be re-implementing a rule the engine already has
     * every piece of — and the next mode would have to write it again. Whether it is allowed at
     * all is the mode's call, once, in {@link fr.ayoub.pvp.api.MatchRules}.
     *
     * <p><b>Direct blows only.</b> A swing, an arrow, a trident — the things one player aims at
     * another. Lava, fire, a fall, a mob is not friendly fire, it is the map, and a player who
     * walks their teammate into a lava pit has earned it.
     *
     * <p>Hurting <b>yourself</b> is not friendly fire either: your own arrow coming back down on
     * your head is between you and physics.
     */
    private static boolean isFriendlyFire(Match match, Player victim, EntityDamageEvent event) {
        if (match.mode().rules().friendlyFire()) {
            return false;
        }

        Player attacker = killerOf(event);
        if (attacker == null || attacker.equals(victim)) {
            return false;
        }

        return match.teamOf(attacker)
                .filter(team -> team.contains(victim.getUniqueId()))
                .isPresent();
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

package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.region.Vec3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The invisible wall.
 *
 * Cancelling the move is what makes it feel solid: the player is simply not allowed
 * to step out, exactly like walking into a block.
 *
 * Only checked when the player actually changes block — a move event fires on every
 * mouse twitch, and doing geometry on all of them would be wasteful.
 */
public final class WallListener implements Listener {

    private static final Component BLOCKED =
            Component.text("You cannot leave the arena", NamedTextColor.RED);

    private final ArenaService arenas;

    public WallListener(ArenaService arenas) {
        this.arenas = arenas;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (!changedBlock(from, to)) {
            return;
        }

        Player player = event.getPlayer();
        arenas.arenaOf(player).ifPresent(arena -> {
            if (arena.bounds().contains(Arena.toVec(to))) {
                return;
            }

            if (arena.bounds().contains(Arena.toVec(from))) {
                // normal case: refuse the step out
                event.setCancelled(true);
            } else {
                // already outside (teleport, glitch, /tp): put them back on the wall
                Vec3 inside = arena.bounds().nearestInside(Arena.toVec(to));
                Location safe = arena.toLocation(inside);
                safe.setYaw(to.getYaw());
                safe.setPitch(to.getPitch());
                player.teleport(safe);
            }
            player.sendActionBar(BLOCKED);
        });
    }

    /** Ender pearls must not be a way out either. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }

        Player player = event.getPlayer();
        arenas.arenaOf(player).ifPresent(arena -> {
            if (!arena.bounds().contains(Arena.toVec(event.getTo()))) {
                event.setCancelled(true);
                player.sendActionBar(BLOCKED);
            }
        });
    }

    private static boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}

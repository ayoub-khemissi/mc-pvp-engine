package fr.ayoub.pvp.core.match;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Freezing a player — without the rubber-band.
 *
 * Cancelling {@code PlayerMoveEvent} does stop the player, but only <b>after the fact</b>:
 * the client has already walked, so the server has to send it back. That correction is the
 * glitch — you see yourself snap backwards. It is unavoidable with that approach, because
 * the client is never told it may not move.
 *
 * So we take the movement away on the <b>client</b> instead: walk speed 0 and jump strength
 * 0 are both sent to the client, which then simply refuses to walk or jump. Nothing moves,
 * so there is nothing to correct and nothing to snap back. The {@code PlayerMoveEvent}
 * check stays, but only as a backstop for things the client does not decide — knockback,
 * a piston, a plugin teleport.
 *
 * Everything here is per-player state that MUST be undone: a player left with walk speed 0
 * cannot move in the lobby either. {@link #release} is called on FIGHT, on death (a
 * spectator has to be able to fly) and by the lobby, which resets it for everyone.
 */
public final class Freeze {

    /** Vanilla defaults. Bukkit exposes them as plain floats, not as attributes. */
    public static final float WALK_SPEED = 0.2f;
    public static final float FLY_SPEED = 0.1f;

    private Freeze() {
    }

    public static void apply(Player player) {
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setSprinting(false);
        jumpStrength(player, 0.0);
    }

    public static void release(Player player) {
        player.setWalkSpeed(WALK_SPEED);
        player.setFlySpeed(FLY_SPEED);

        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(jump.getDefaultValue());
        }
    }

    private static void jumpStrength(Player player, double value) {
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(value);
        }
    }
}

package fr.ayoub.pvp.mode.fortress;

import org.bukkit.Material;

import java.util.Map;

/**
 * The item you hold to place a block — which is not always the block itself.
 *
 * Redstone dust on the ground is the block {@code REDSTONE_WIRE}; the thing in your hand is
 * the item {@code REDSTONE}. String laid out is {@code TRIPWIRE}; in your hand it is
 * {@code STRING}. Ask Bukkit for an ItemStack of the block form and it <b>throws</b>.
 *
 * That is not a theoretical problem: adding redstone to the palette made the "allowed
 * blocks" screen die halfway through rendering page two, which looked for all the world
 * like a broken paginator. The bug was three lines away from where it appeared.
 *
 * The palette names <b>blocks</b>, because a block is what the engine sees land in the
 * world. This is the one place that translates back.
 */
public final class PlacingItems {

    /** Blocks whose item form has a different name. */
    private static final Map<Material, Material> OVERRIDES = Map.of(
            Material.REDSTONE_WIRE, Material.REDSTONE,
            Material.TRIPWIRE, Material.STRING);

    private PlacingItems() {
    }

    /** @return the item that places this block, or null if there is none */
    public static Material of(Material block) {
        Material override = OVERRIDES.get(block);
        if (override != null) {
            return override;
        }
        return block.isItem() ? block : null;
    }

    public static Material of(String blockId) {
        Material block = Material.matchMaterial(blockId);
        return block == null ? null : of(block);
    }
}

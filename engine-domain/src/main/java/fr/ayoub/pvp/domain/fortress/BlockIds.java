package fr.ayoub.pvp.domain.fortress;

/**
 * Reading the block <b>type</b> out of a block <b>state</b>.
 *
 * A blueprint stores the full state a block was placed in —
 * {@code minecraft:oak_stairs[facing=east,half=bottom,…]} — because a fortress is made of
 * doors, stairs, ladders, levers and pistons, and every one of those means something
 * different depending on which way it points. Store only the name and a pasted fortress has
 * all its stairs facing east and all its doors in two identical bottom halves.
 *
 * The <b>rules</b>, on the other hand, are about the type: the obsidian budget does not care
 * which way the obsidian faces, and both halves of a door cost the same door.
 *
 * This is the one place that goes from one to the other. Pure string work — no Bukkit, so
 * it is unit-tested rather than play-tested.
 */
public final class BlockIds {

    private static final String NAMESPACE = "minecraft:";

    private BlockIds() {
    }

    /** {@code minecraft:oak_door[half=upper]} → {@code OAK_DOOR}. */
    public static String typeOf(String blockState) {
        if (blockState == null || blockState.isBlank()) {
            return Blueprint.AIR;
        }

        String id = blockState.trim();

        int state = id.indexOf('[');
        if (state >= 0) {
            id = id.substring(0, state);
        }
        if (id.startsWith(NAMESPACE)) {
            id = id.substring(NAMESPACE.length());
        }
        return id.toUpperCase(java.util.Locale.ROOT);
    }
}

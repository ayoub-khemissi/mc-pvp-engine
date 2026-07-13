package fr.ayoub.pvp.mode.fortress.build;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;

import java.util.List;

/**
 * The furniture of a build zone: the buttons, the board that explains the mode, and the
 * arrow that says which way the fortress faces.
 *
 * The buttons are <b>blocks</b>, not items. The builder is in creative, where an item can be
 * thrown away, stacked, or lost in the creative inventory — a block bolted to the floor
 * cannot be any of those, needs no command, and cannot be broken (the listener refuses
 * everything outside the cube).
 *
 * Every piece of text here goes through {@link SignText}, which refuses what Minecraft
 * would quietly cut in half.
 */
public final class BuildRoom {

    /** How far in front of the cube the arrow is painted. */
    private static final int ARROW_LENGTH = 6;

    private static final Material ARROW = Material.GOLD_BLOCK;

    private BuildRoom() {
    }

    // --- the buttons ---------------------------------------------------------------

    private static int[] offset(RoomButton button) {
        return switch (button) {
            case SAVE -> new int[]{3, 1, 4};
            case CLEAR -> new int[]{3, 1, 6};
            case PALETTE -> new int[]{3, 1, 8};
            case EXIT -> new int[]{3, 1, 10};
        };
    }

    private static Material material(RoomButton button) {
        return switch (button) {
            case SAVE -> Material.LIME_CONCRETE;
            case CLEAR -> Material.ORANGE_CONCRETE;
            case PALETTE -> Material.CHEST;
            case EXIT -> Material.RED_CONCRETE;
        };
    }

    private static NamedTextColor colour(RoomButton button) {
        return switch (button) {
            case SAVE -> NamedTextColor.DARK_GREEN;
            case CLEAR -> NamedTextColor.GOLD;
            case PALETTE -> NamedTextColor.DARK_AQUA;
            case EXIT -> NamedTextColor.DARK_RED;
        };
    }

    /** Which button was right-clicked, if any. */
    public static RoomButton buttonAt(BuildZone zone, Block block) {
        for (RoomButton button : RoomButton.values()) {
            if (blockOf(zone, button).equals(block)) {
                return button;
            }
        }
        return null;
    }

    /** The buttons and their signs are furniture: they cannot be broken or edited. */
    public static boolean isFurniture(BuildZone zone, Block block) {
        for (RoomButton button : RoomButton.values()) {
            Block at = blockOf(zone, button);
            if (at.equals(block) || at.getRelative(0, 1, 0).equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static Block blockOf(BuildZone zone, RoomButton button) {
        int[] at = offset(button);
        ZoneGeometry g = zone.geometry();
        return zone.world().getBlockAt(g.roomX() + at[0], g.roomY() + at[1], g.roomZ() + at[2]);
    }

    // --- building it ----------------------------------------------------------------

    public static void furnish(BuildZone zone) {
        for (RoomButton button : RoomButton.values()) {
            Block block = blockOf(zone, button);
            block.setType(material(button));

            write(block.getRelative(0, 1, 0),
                    Component.text(button.label(), colour(button)),
                    SignText.wrap(button.hint(), SignText.MAX_LINES - 1),
                    NamedTextColor.BLACK);
        }

        placeBoard(zone);
        placeFrontArrow(zone);
    }

    /** The board: what this place is, and the two rules nobody guesses on their own. */
    private static void placeBoard(BuildZone zone) {
        ZoneGeometry g = zone.geometry();

        for (int i = 0; i < BoardText.PANELS.size(); i++) {
            BoardText.Panel panel = BoardText.PANELS.get(i);

            Block block = zone.world().getBlockAt(g.roomX() + 5 + i, g.roomY() + 1, g.roomZ() + 3);

            write(block,
                    Component.text(panel.title(), titleColour(i)),
                    SignText.wrap(panel.body(), SignText.MAX_LINES - 1),
                    NamedTextColor.BLACK);
        }
    }

    private static NamedTextColor titleColour(int panel) {
        return switch (panel) {
            case 0 -> NamedTextColor.DARK_PURPLE;
            case 1 -> NamedTextColor.DARK_RED;
            case 2 -> NamedTextColor.DARK_AQUA;
            default -> NamedTextColor.DARK_GREEN;
        };
    }

    /**
     * The arrow on the floor, in front of the fortress.
     *
     * A fortress is not symmetric — the gate goes on one side, the sheer wall on the other
     * — so the builder has to know which face the enemy will arrive at. That face is
     * {@code z = 0} of the blueprint (see {@code Blueprint}), and this is the arrow that
     * says so without anybody having to read the docs.
     */
    private static void placeFrontArrow(BuildZone zone) {
        ZoneGeometry g = zone.geometry();

        int centre = g.cubeX() + g.cubeSize() / 2;
        int front = g.cubeZ();          // the z = 0 face of the blueprint
        int floor = g.roomY();

        for (int i = 1; i <= ARROW_LENGTH - 1; i++) {
            zone.world().getBlockAt(centre, floor, front - i).setType(ARROW);
        }

        zone.world().getBlockAt(centre, floor, front - ARROW_LENGTH).setType(ARROW);
        for (int i = 1; i <= 2; i++) {
            zone.world().getBlockAt(centre - i, floor, front - ARROW_LENGTH + i).setType(ARROW);
            zone.world().getBlockAt(centre + i, floor, front - ARROW_LENGTH + i).setType(ARROW);
        }

        write(zone.world().getBlockAt(centre, floor + 1, front - ARROW_LENGTH - 1),
                Component.text("FRONT", NamedTextColor.DARK_RED),
                SignText.wrap("The enemy comes from this way", SignText.MAX_LINES - 1),
                NamedTextColor.BLACK);
    }

    /** A standing sign, facing south — which is where the builder stands. */
    private static void write(Block block, Component title, List<String> body, NamedTextColor colour) {
        block.setType(Material.OAK_SIGN);

        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(BlockFace.SOUTH);
            block.setBlockData(rotatable);
        }

        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        sign.getSide(Side.FRONT).line(0, title);
        for (int i = 0; i < body.size() && i + 1 < SignText.MAX_LINES; i++) {
            sign.getSide(Side.FRONT).line(i + 1, Component.text(body.get(i), colour));
        }

        sign.setWaxed(true);   // nobody edits the furniture
        sign.update();
    }
}

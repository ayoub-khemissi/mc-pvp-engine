package fr.ayoub.pvp.mode.fortress.build;

import java.util.List;

/**
 * What the board in the build zone says.
 *
 * Plain strings, on purpose: the copy is the thing most likely to be edited, and it is the
 * thing Minecraft silently truncates. Keeping it here — with no Bukkit anywhere near it —
 * is what lets a unit test check that every panel still fits on a sign.
 */
public final class BoardText {

    /** @param body one sentence; {@link SignText} breaks it across the lines under the title */
    public record Panel(String title, String body) {
    }

    public static final List<Panel> PANELS = List.of(
            new Panel("FORTRESS", "Build inside the square on the floor"),
            new Panel("END CRYSTAL", "Put it on obsidian and leave 3x3 clear"),
            new Panel("BLOCKS", "Only allowed ones. Open the chest"),
            new Panel("IN A MATCH", "The enemy digs in. Crystal dies = you lose"));

    private BoardText() {
    }
}

package fr.ayoub.pvp.mode.fortress.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitting words onto a Minecraft sign.
 *
 * A sign line is limited by <b>width</b>, and Minecraft enforces it by <b>silently cutting
 * the text</b>. A line that reads "Crystal dies=you lose" in the code renders as
 * "Crystal dies=you" on the wall, and nobody ever finds out — the sign is simply wrong, and
 * it says so with total confidence.
 *
 * So: wrap on word boundaries, and <b>throw</b> when the text does not fit. A build that
 * cannot render its own signs should fail in a test, not in front of a player.
 *
 * The limit is really pixels — the font is not monospaced — so {@link #MAX_CHARS} is
 * deliberately conservative: it is the widest that even a line of all-capital "W"s survives.
 */
public final class SignText {

    /** Conservative: the real limit is ~90 pixels, and a capital W is 6 of them. */
    public static final int MAX_CHARS = 15;

    /** A sign has four lines. The first is usually a title. */
    public static final int MAX_LINES = 4;

    private SignText() {
    }

    /**
     * @param maxLines how many lines are left for this text (3 under a title)
     * @throws IllegalArgumentException if it cannot be made to fit — which is the point
     */
    public static List<String> wrap(String text, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.trim().split("\\s+")) {
            if (word.length() > MAX_CHARS) {
                throw new IllegalArgumentException(
                        "'" + word + "' is too wide for a sign (" + MAX_CHARS + " chars max)");
            }

            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= MAX_CHARS) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        if (lines.size() > maxLines) {
            throw new IllegalArgumentException("this needs " + lines.size() + " lines and only "
                    + maxLines + " are free — Minecraft would cut it off without saying so: \""
                    + text + "\"");
        }
        return lines;
    }
}

package fr.ayoub.pvp.mode.fortress.build;

/**
 * The four buttons on the floor of a build zone, and what their signs say.
 *
 * Deliberately Bukkit-free. The words are the part that Minecraft silently truncates, so
 * the words are the part a unit test has to be able to reach — and a test cannot touch a
 * class that needs a server to load.
 */
public enum RoomButton {

    SAVE("[ SAVE ]", "overwrite slot"),
    CLEAR("[ CLEAR ]", "empty the cube"),
    PALETTE("[ BLOCKS ]", "allowed blocks"),
    EXIT("[ EXIT ]", "back to lobby");

    private final String label;
    private final String hint;

    RoomButton(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }
}

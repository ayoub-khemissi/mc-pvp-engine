package fr.ayoub.pvp.domain.match;

/**
 * How many teams, and how many players in each: "1v1", "2v2", "5v5", even "1v1v1".
 *
 * <p>A rating is stored per (mode, format), so your 1v1 rank is not your 3v3 rank.
 *
 * <p>The optional {@code label} overrides the computed id, and exists for one reason: a battle
 * royale is a <b>single</b> rating pool — "solo" — however many actually join. Modelled as
 * {@code Format(24, 1)} its id would be "1v1v…v1" and, worse, would change with the lobby size, so
 * an 18-player match and a 24-player match would be two different pools. {@link #solo} pins the id
 * to "solo" so they are one. For everything else the label is null and nothing changes.
 */
public record Format(int teams, int playersPerTeam, String label) {

    public Format {
        if (teams < 2) {
            throw new IllegalArgumentException("a match needs at least 2 teams, got " + teams);
        }
        if (playersPerTeam < 1) {
            throw new IllegalArgumentException("a team needs at least 1 player, got " + playersPerTeam);
        }
    }

    /** The everyday format: a computed id, no label. Keeps every existing call site working. */
    public Format(int teams, int playersPerTeam) {
        this(teams, playersPerTeam, null);
    }

    /**
     * A battle royale: up to {@code cap} solo players, one rating pool called "solo".
     *
     * <p>The cap is only the ceiling — the queue starts a match with however many it gathered, and
     * they all share this format's identity, so the rating and the queue never fragment by size.
     */
    public static Format solo(int cap) {
        return new Format(cap, 1, "solo");
    }

    /** Parses "1v1", "3v3", "1v1v1"… All teams must be the same size. */
    public static Format parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty format");
        }

        String[] parts = text.split("v");
        if (parts.length < 2) {
            throw new IllegalArgumentException("bad format '" + text + "' (expected something like 1v1)");
        }

        int size;
        try {
            size = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad format '" + text + "' (expected something like 1v1)");
        }

        for (String part : parts) {
            int other;
            try {
                other = Integer.parseInt(part.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad format '" + text + "' (expected something like 1v1)");
            }
            if (other != size) {
                throw new IllegalArgumentException("teams must be the same size, got '" + text + "'");
            }
        }

        return new Format(parts.length, size);
    }

    public int totalPlayers() {
        return teams * playersPerTeam;
    }

    /** "1v1", "2v2"… or the label if one was given ("solo"). Also the value stored in the database. */
    public String id() {
        return label != null ? label : (playersPerTeam + "v").repeat(teams - 1) + playersPerTeam;
    }

    @Override
    public String toString() {
        return id();
    }
}

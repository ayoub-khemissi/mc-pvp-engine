package fr.ayoub.pvp.domain.match;

/**
 * How many teams, and how many players in each: "1v1", "2v2", "5v5", even "1v1v1".
 *
 * A rating is stored per (mode, format), so your 1v1 rank is not your 3v3 rank.
 */
public record Format(int teams, int playersPerTeam) {

    public Format {
        if (teams < 2) {
            throw new IllegalArgumentException("a match needs at least 2 teams, got " + teams);
        }
        if (playersPerTeam < 1) {
            throw new IllegalArgumentException("a team needs at least 1 player, got " + playersPerTeam);
        }
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

    /** "1v1", "2v2"… Also the value stored in the database. */
    public String id() {
        return (playersPerTeam + "v").repeat(teams - 1) + playersPerTeam;
    }

    @Override
    public String toString() {
        return id();
    }
}

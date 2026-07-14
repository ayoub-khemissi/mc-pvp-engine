package fr.ayoub.pvp.api;

/**
 * @param rounds           best-of; must be odd (1, 3, 5…) so a series always has a winner
 * @param countdownSeconds 3 · 2 · 1 · FIGHT — before every round
 * @param timeLimitSeconds 0 = no limit
 * @param building         may players break and place blocks?
 *                         <p>
 *                         This is not just a permission, it decides the <b>game mode</b>.
 *                         {@code false} → ADVENTURE: the client itself refuses to break a
 *                         block, so nothing is even animated. {@code true} → SURVIVAL, and
 *                         the engine stops cancelling breaks and placements — for a mode
 *                         that is about building (a bridge, a wall).
 * @param friendlyFire     can teammates hurt each other?
 *                         <p>
 *                         Almost always {@code false}, and it lives here rather than in a mode
 *                         because the <b>engine</b> is what knows the teams. A mode that had to
 *                         cancel its own teammates' blows would be re-implementing a rule the
 *                         engine is already holding all the pieces of — and every other mode
 *                         would have to re-implement it again.
 *                         <p>
 *                         Only <b>direct</b> blows are stopped: a swing, an arrow, a trident. If
 *                         a player walks their teammate into lava, that is between them.
 * @param dropItems        may a player throw something out of their inventory?
 *                         <p>
 *                         {@code false} for a kit mode: a duel is five minutes long, nothing in
 *                         the inventory was earned, and dropping only ever litters the arena.
 *                         {@code true} for a mode where the inventory is <b>played</b> — where
 *                         blocks are mined, chests are looted, and handing a teammate the last
 *                         stack of obsidian is a move. Cancelling that is not a safety net, it
 *                         is a missing feature.
 *                         <p>
 *                         The dead never drop, whatever this says: a spectator has no hands.
 * @param spawnProtectionSeconds how long a player who just respawned cannot be hurt. 0 = none.
 *                         <p>
 *                         Any mode with respawn needs it, or it has a spawn camp in it: the
 *                         strongest play becomes standing on the enemy's pad and killing them as
 *                         they materialise, which they can do nothing about — they are dead before
 *                         the world has drawn itself around them.
 *                         <p>
 *                         It <b>ends the instant they attack</b>. A player who can hit while they
 *                         cannot be hit has not been given a shield, they have been given seconds
 *                         of free damage: die on purpose, walk into the fight, swing with impunity.
 */
public record MatchRules(int rounds, int countdownSeconds, int timeLimitSeconds,
                         boolean building, int respawnSeconds, int setupSeconds,
                         boolean friendlyFire, boolean dropItems, int spawnProtectionSeconds) {

    /**
     * How long the mode may take in {@code onPrepare} before the engine gives up on it.
     *
     * This killed Fortress. The engine had a flat ten-second limit, and Fortress runs a
     * thirty-second vote: the match was aborted mid-vote, the players were dumped back in the
     * lobby — and then the vote finished anyway and pasted two fortresses and two crystals
     * into a dead match, on an arena that had already been handed to somebody else.
     *
     * A limit the engine invents is a limit that is wrong for every mode but the one it was
     * written for. The mode knows how long it needs. This is it saying so, and the engine's
     * timeout is what it always should have been: a net under a <b>broken</b> mode, not a
     * budget for a working one.
     */
    public int setupTimeoutSeconds() {
        return setupSeconds;
    }

    public MatchRules {
        if (rounds < 1 || rounds % 2 == 0) {
            throw new IllegalArgumentException("best-of must be odd (1, 3, 5…), got " + rounds);
        }
        if (countdownSeconds < 0) {
            throw new IllegalArgumentException("countdown cannot be negative");
        }
        if (respawnSeconds < 0) {
            throw new IllegalArgumentException("respawn delay cannot be negative");
        }
        if (spawnProtectionSeconds < 0) {
            throw new IllegalArgumentException("spawn protection cannot be negative");
        }
    }

    /** Best of 3, no building, no respawn — one lucky hit should not decide a ranked match. */
    public static MatchRules standard() {
        return new MatchRules(3, 5, 300, false, 0, 10, false, false, 0);
    }

    /**
     * Do the dead come back?
     *
     * <b>0 means they do not</b>, and that changes the whole shape of a match: a duel ends
     * when a team is wiped out, so "last team standing" IS the win condition. Turn respawn on
     * and that condition disappears — everybody is always coming back — so the mode has to
     * bring a win condition of its own (a crystal, a payload, a clock). The engine cannot
     * guess one, and a match with neither would simply never end.
     */
    public boolean hasRespawn() {
        return respawnSeconds > 0;
    }

    public boolean hasSpawnProtection() {
        return spawnProtectionSeconds > 0;
    }

    public boolean hasTimeLimit() {
        return timeLimitSeconds > 0;
    }
}

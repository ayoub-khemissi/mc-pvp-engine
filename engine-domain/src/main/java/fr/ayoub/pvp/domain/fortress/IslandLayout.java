package fr.ayoub.pvp.domain.fortress;

/**
 * Where the islands and the voting plains sit — and, which is the only interesting part,
 * <b>how far apart</b>.
 *
 * <p>Three things must never be visible from each other:
 *
 * <ul>
 *   <li><b>A plain and an island.</b> A team choosing its fortress must not be able to read the
 *       map it is about to fight on, and a player in the match must not have a slab of leftover
 *       voting scenery hanging over their head.
 *   <li><b>The two plains.</b> They were once 36 blocks apart, which let a team stand on its own
 *       and read the enemy's three fortresses like a menu.
 *   <li><b>Two islands.</b> Two matches running at once are two matches, not a spectator sport.
 * </ul>
 *
 * <p>Each of those was fixed, once, by picking a bigger number by hand — against the view
 * distance <em>of that day</em>. Then the view distance was raised for an unrelated reason
 * (rendering an arena costs chunks, and a 128-block island needs to be seen across) and two of
 * them silently broke again, because the numbers never knew what they depended on.
 *
 * <p>So now they do. This layout is handed the sight radius and <b>refuses to exist</b> if
 * anything a player must not see is inside it. Nothing here is a wall: a spectator flies through
 * walls, and a barrier you can see past is decoration. Distance is the only mechanism that
 * actually works — and in a void world, where the chunks in between hold nothing and are never
 * loaded because nobody is ever there, distance is free.
 */
public record IslandLayout(
        int islandSize,
        int spacing,
        int voteZ0,
        int voteZ1,
        int plainDepth,
        int sight) {

    /**
     * How far a player can actually see, in blocks, at a given view distance.
     *
     * <p>Not {@code chunks × 16}. The server sends whole <b>chunks</b>: a player standing at the
     * edge of their own chunk still gets every chunk within N of it, and the thing they must not
     * see may begin one block inside the last one. That is exactly how a plain 300 blocks away
     * became visible at a view distance of 10 — 172 blocks of gap, but the chunk it started in
     * was the tenth. Two chunks of slop, and the arithmetic stops being a trap.
     */
    public static int sightOf(int viewDistanceChunks) {
        return (viewDistanceChunks + 2) * 16;
    }

    public IslandLayout {
        check(spacing - islandSize, sight,
                "two instances would be able to see each other");
        check(voteZ0 - islandSize, sight,
                "a match would be able to see the voting plain, and the plain the match");
        check(voteZ1 - (voteZ0 + plainDepth), sight,
                "one team would be able to see the other team's fortresses while voting");
    }

    /** Where instance {@code index} begins, along X. */
    public int originX(int index) {
        return index * spacing;
    }

    /** Where a team's voting plain begins, along Z, relative to the island's origin. */
    public int votePlainZ(int team) {
        return team == 0 ? voteZ0 : voteZ1;
    }

    public int gapBetweenIslands() {
        return spacing - islandSize;
    }

    public int gapFromIslandToPlain() {
        return voteZ0 - islandSize;
    }

    public int gapBetweenPlains() {
        return voteZ1 - (voteZ0 + plainDepth);
    }

    private static void check(int gap, int sight, String what) {
        if (gap < sight) {
            throw new IllegalArgumentException(
                    what + ": " + gap + " blocks apart, but a player sees " + sight);
        }
    }
}

package fr.ayoub.pvp.domain.fortress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Is this fortress playable?
 *
 * Two jobs, and they are not the same:
 * <ul>
 *   <li>The build menu asks this to decide whether a fortress may be picked for a match.
 *       A build that fails is a draft, not an error.</li>
 *   <li>It is also the <b>last line of defence</b>: the build zone refuses illegal blocks
 *       as they are placed, but a fortress loaded from the database was written by an
 *       older version of the rules, or by a bug. It is re-checked before it is pasted.</li>
 * </ul>
 *
 * Pure — no Bukkit, so every rule below is unit-tested with no server.
 */
public final class FortressValidator {

    private FortressValidator() {
    }

    public static BuildReport validate(Blueprint blueprint, BuildRules rules) {
        List<String> problems = new ArrayList<>();

        checkSize(blueprint, rules, problems);
        checkCrystal(blueprint, rules, problems);
        checkPalette(blueprint, rules, problems);
        checkQuotas(blueprint, rules, problems);

        return problems.isEmpty() ? BuildReport.ok() : BuildReport.failed(problems);
    }

    /**
     * The cube is a config knob, and a fortress carries the size it was built at.
     *
     * A 10³ build cannot be pasted into a 20³ pad — it would sit in one corner of it. It has
     * to be rebuilt, not silently stretched, and the player has to be told so rather than
     * find out when their fortress turns up a quarter of the size it should be.
     */
    private static void checkSize(Blueprint blueprint, BuildRules rules, List<String> problems) {
        if (blueprint.size() != rules.size()) {
            problems.add("This fortress was built at size " + blueprint.size()
                    + "³ and the server now uses " + rules.size() + "³ — it has to be rebuilt.");
        }
    }

    /**
     * The crystal is what a match is won on, so it may not be buried.
     *
     * It must stand on its base block, and keep a pocket of air around it. That pocket is
     * the reason a fortress is a fortress and not a solid cube of obsidian.
     */
    private static void checkCrystal(Blueprint blueprint, BuildRules rules, List<String> problems) {
        BlockPos crystal = blueprint.crystal();

        if (crystal == null) {
            problems.add("No End Crystal: place one to make this fortress playable.");
            return;
        }
        if (!blueprint.contains(crystal)) {
            problems.add("The End Crystal is outside the fortress.");
            return;
        }

        BlockPos base = crystal.below();
        if (!blueprint.contains(base)) {
            problems.add("The End Crystal has nothing under it — it needs a "
                    + rules.crystalBase() + " block to stand on.");
            return;
        }

        String under = blueprint.typeAt(base);
        if (!under.equals(rules.crystalBase())) {
            problems.add("The End Crystal must stand on " + rules.crystalBase()
                    + ", not on " + (Blueprint.AIR.equals(under) ? "thin air" : under) + ".");
        }

        checkClearance(blueprint, rules, crystal, problems);
    }

    /** The air pocket: radius wide, height tall, starting at the crystal itself. */
    private static void checkClearance(Blueprint blueprint, BuildRules rules,
                                       BlockPos crystal, List<String> problems) {
        int radius = rules.clearanceRadius();

        for (int dy = 0; dy < rules.clearanceHeight(); dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = crystal.offset(dx, dy, dz);

                    // A crystal near an edge has part of its clearance outside the cube.
                    // That is not a problem: there is nothing there to remove.
                    if (!blueprint.contains(pos) || blueprint.isAir(pos)) {
                        continue;
                    }
                    problems.add("A " + blueprint.typeAt(pos) + " at " + pos
                            + " is inside the End Crystal's clearance — it must stay open.");
                    return;   // one is enough; the menu shows where, the player sees the rest
                }
            }
        }
    }

    private static void checkPalette(Blueprint blueprint, BuildRules rules, List<String> problems) {
        for (String block : blueprint.counts().keySet()) {
            if (!rules.allows(block)) {
                problems.add(block + " is not allowed in a fortress.");
            }
        }
    }

    private static void checkQuotas(Blueprint blueprint, BuildRules rules, List<String> problems) {
        for (Map.Entry<String, Integer> used : blueprint.counts().entrySet()) {
            if (!rules.allows(used.getKey())) {
                continue;   // already reported, and it has no quota to exceed
            }
            int quota = rules.quota(used.getKey());
            if (used.getValue() > quota) {
                problems.add("Too many " + used.getKey() + ": "
                        + used.getValue() + " placed, only " + quota + " allowed.");
            }
        }
    }
}

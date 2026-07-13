package fr.ayoub.pvp.domain.fortress;

import java.util.Map;
import java.util.Objects;

/**
 * What a player is allowed to build.
 *
 * <b>Every number here is a knob.</b> Fortress is an experiment: the cube size, the block
 * list, how many of each, and how much room the End Crystal needs are all still being
 * tuned. None of them may be hard-coded anywhere else — they come from config.yml, land
 * here, and this is the only thing the rules read.
 *
 * @param size             the fortress cube, in blocks (20 today)
 * @param allowance        block id → how many of it a fortress may contain.
 *                         A block that is not a key here cannot be placed at all.
 * @param crystalBase      the block the End Crystal must stand on ("OBSIDIAN")
 * @param clearanceRadius  how far around the crystal must stay empty.
 *                         1 → a 3×3 footprint, 2 → 5×5.
 * @param clearanceHeight  how many blocks above the base must stay empty
 */
public record BuildRules(
        int size,
        Map<String, Integer> allowance,
        String crystalBase,
        int clearanceRadius,
        int clearanceHeight) {

    public BuildRules {
        Objects.requireNonNull(allowance, "allowance");
        Objects.requireNonNull(crystalBase, "crystalBase");

        if (size < 1) {
            throw new IllegalArgumentException("a fortress needs a positive size, got " + size);
        }
        if (clearanceRadius < 0) {
            throw new IllegalArgumentException("a clearance radius cannot be negative");
        }
        if (clearanceHeight < 1) {
            throw new IllegalArgumentException("the crystal needs at least one block of headroom");
        }
        allowance = Map.copyOf(allowance);
    }

    public boolean allows(String block) {
        return allowance.containsKey(block);
    }

    public int quota(String block) {
        return allowance.getOrDefault(block, 0);
    }

    /** How many more of this block the player may still place. Never negative. */
    public int remaining(Blueprint blueprint, String block) {
        int used = blueprint.counts().getOrDefault(block, 0);
        return Math.max(0, quota(block) - used);
    }

    /** A fresh, empty build with this rule's size. */
    public Blueprint emptyBlueprint() {
        return new Blueprint(size);
    }
}

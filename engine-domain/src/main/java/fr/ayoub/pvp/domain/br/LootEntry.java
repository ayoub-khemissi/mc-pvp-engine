package fr.ayoub.pvp.domain.br;

import java.util.Map;

/**
 * One line of a loot table: an item that <em>might</em> come out of a chest, and how likely.
 *
 * @param material      the block/item id, e.g. {@code "minecraft:diamond_sword"}. A name, not a
 *                      Bukkit type — the domain does not know about Bukkit
 * @param minCount      fewest of it in a stack when it rolls
 * @param maxCount      most of it in a stack
 * @param enchantments  enchantment id → level, baked on when it rolls ({@code sharpness -> 3}). Empty
 *                      for a plain item
 * @param weight        its share of the draw, relative to every other entry. Weight 9 against weight
 *                      1 comes out nine times as often. This is the only rarity knob there is
 */
public record LootEntry(String material, int minCount, int maxCount,
                        Map<String, Integer> enchantments, int weight) {

    public LootEntry {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("a loot entry needs a material");
        }
        if (minCount < 1 || maxCount < minCount) {
            throw new IllegalArgumentException(
                    "bad count range " + minCount + ".." + maxCount + " for " + material);
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive for " + material);
        }
        enchantments = Map.copyOf(enchantments);
    }
}

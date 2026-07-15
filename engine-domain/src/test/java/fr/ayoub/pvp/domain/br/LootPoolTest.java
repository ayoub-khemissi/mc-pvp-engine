package fr.ayoub.pvp.domain.br;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What comes out of a chest.
 *
 * <p>The map ships with empty chests; the mode fills them at the start of a match from a weighted
 * pool. This is that roll, and it is pure: hand it a {@link Random} and it is fully deterministic, so
 * the balance of the loot table can be tested without ever placing a block — an item that is meant to
 * be one-in-fifty can be shown to actually be one-in-fifty.
 *
 * <p>The item is described as data (a material name, a count, enchantments as names→levels), never as
 * a Bukkit {@code ItemStack}. Turning that into a real stack is the mode's job; deciding what the
 * stack <b>is</b> stays here, where it can be reasoned about.
 */
class LootPoolTest {

    private static LootEntry item(String material, int weight) {
        return new LootEntry(material, 1, 1, Map.of(), weight);
    }

    /** A single-entry pool that rolls exactly one item: that item, every time. */
    @Test
    void rollsFromASingleEntry() {
        LootPool pool = new LootPool(1, 1, List.of(item("minecraft:iron_sword", 1)));

        List<RolledItem> rolled = pool.roll(new Random(0));

        assertEquals(1, rolled.size());
        assertEquals("minecraft:iron_sword", rolled.get(0).material());
        assertEquals(1, rolled.get(0).count());
    }

    @Test
    void rollsBetweenTheMinAndMaxNumberOfItems() {
        LootPool pool = new LootPool(2, 4, List.of(item("minecraft:apple", 1)));

        for (int seed = 0; seed < 100; seed++) {
            int n = pool.roll(new Random(seed)).size();
            assertTrue(n >= 2 && n <= 4, "rolled " + n);
        }
    }

    /** The whole point of a weight. Over many rolls, a weight-9 item beats a weight-1 item ~9:1. */
    @Test
    void respectsTheWeights() {
        LootPool pool = new LootPool(1, 1, List.of(
                item("minecraft:diamond", 1),
                item("minecraft:dirt", 9)));

        int dirt = 0;
        int total = 100_000;
        Random random = new Random(42);
        for (int i = 0; i < total; i++) {
            if (pool.roll(random).get(0).material().equals("minecraft:dirt")) {
                dirt++;
            }
        }

        double share = (double) dirt / total;
        assertTrue(share > 0.86 && share < 0.94, "dirt came out " + share + " of the time");
    }

    @Test
    void rollsACountInsideTheItemsRange() {
        LootPool pool = new LootPool(1, 1, List.of(new LootEntry("minecraft:arrow", 8, 16, Map.of(), 1)));

        for (int seed = 0; seed < 100; seed++) {
            int count = pool.roll(new Random(seed)).get(0).count();
            assertTrue(count >= 8 && count <= 16, "count " + count);
        }
    }

    @Test
    void carriesTheEnchantmentsOfTheEntryItRolled() {
        LootPool pool = new LootPool(1, 1, List.of(
                new LootEntry("minecraft:diamond_sword", 1, 1,
                        Map.of("minecraft:sharpness", 3), 1)));

        RolledItem rolled = pool.roll(new Random(0)).get(0);

        assertEquals(Map.of("minecraft:sharpness", 3), rolled.enchantments());
    }

    /** The same chest, rolled from the same seed, is the same chest. Matches must be reproducible. */
    @Test
    void isDeterministicForAGivenSeed() {
        LootPool pool = new LootPool(1, 3, List.of(
                item("minecraft:iron_ingot", 3),
                item("minecraft:gold_ingot", 2),
                item("minecraft:emerald", 1)));

        assertEquals(pool.roll(new Random(7)), pool.roll(new Random(7)));
    }

    @Test
    void refusesAnEmptyPool() {
        assertThrows(IllegalArgumentException.class, () -> new LootPool(1, 1, List.of()));
    }

    @Test
    void refusesABackwardsRollRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new LootPool(3, 1, List.of(item("minecraft:stone", 1))));
    }

    @Test
    void refusesAWeightlessEntry() {
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:x", 1, 1, Map.of(), 0));
    }

    @Test
    void refusesABackwardsCountRange() {
        assertThrows(IllegalArgumentException.class, () -> new LootEntry("minecraft:x", 5, 2, Map.of(), 1));
    }
}

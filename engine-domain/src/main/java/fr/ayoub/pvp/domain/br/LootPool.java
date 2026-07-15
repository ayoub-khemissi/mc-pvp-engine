package fr.ayoub.pvp.domain.br;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A weighted table a chest is filled from.
 *
 * <p>Each chest rolls somewhere between {@code minRolls} and {@code maxRolls} items, and every roll
 * is an independent weighted draw over the entries. Weight is the only rarity knob: a diamond sword
 * at weight 1 against dirt at weight 99 is a one-in-a-hundred find, and that ratio is exactly what a
 * unit test can pin down, because the whole thing is driven by a {@link Random} the caller passes in.
 *
 * <p>Bukkit-free by construction — it deals in {@link RolledItem}, not {@code ItemStack}. The mode
 * converts. What the item <b>is</b> is decided here, where it can be reasoned about and balanced;
 * turning it into a real stack is mechanical.
 */
public final class LootPool {

    private final int minRolls;
    private final int maxRolls;
    private final List<LootEntry> entries;
    private final int totalWeight;

    public LootPool(int minRolls, int maxRolls, List<LootEntry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("a loot pool with no entries fills nothing");
        }
        if (minRolls < 0 || maxRolls < minRolls) {
            throw new IllegalArgumentException("bad roll range " + minRolls + ".." + maxRolls);
        }
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
        this.entries = List.copyOf(entries);
        this.totalWeight = entries.stream().mapToInt(LootEntry::weight).sum();
    }

    /** Fill one chest. Deterministic for a given {@code random}, so a match can be reproduced. */
    public List<RolledItem> roll(Random random) {
        int rolls = minRolls + (maxRolls == minRolls ? 0 : random.nextInt(maxRolls - minRolls + 1));

        List<RolledItem> items = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = pick(random);
            int count = entry.minCount()
                    + (entry.maxCount() == entry.minCount()
                        ? 0 : random.nextInt(entry.maxCount() - entry.minCount() + 1));
            items.add(new RolledItem(entry.material(), count, entry.enchantments()));
        }
        return items;
    }

    private LootEntry pick(Random random) {
        int roll = random.nextInt(totalWeight);
        for (LootEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);   // unreachable: the weights sum to totalWeight
    }
}

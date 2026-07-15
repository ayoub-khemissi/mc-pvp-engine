package fr.ayoub.pvp.domain.br;

import java.util.Map;

/**
 * A concrete item a roll produced: this material, this many, with these enchantments.
 *
 * <p>Still data, not a Bukkit {@code ItemStack} — the mode turns it into one when it fills the chest.
 * Keeping it a value means a chest's contents can be asserted in a unit test.
 */
public record RolledItem(String material, int count, Map<String, Integer> enchantments) {

    public RolledItem {
        enchantments = Map.copyOf(enchantments);
    }
}

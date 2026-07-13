package fr.ayoub.pvp.mode.fortress.storage;

import fr.ayoub.pvp.domain.fortress.Blueprint;

import java.util.UUID;

/**
 * One of a player's fortress slots.
 *
 * @param playable  did it pass {@code FortressValidator} when it was saved? A fortress that
 *                  did not is a <b>draft</b>: it saves, it loads, it just cannot be taken
 *                  into a match. Stored beside the blueprint so the menu can grey it out
 *                  without decoding and re-checking every slot.
 * @param isDefault the one a match falls back on. Exactly one per player.
 */
public record SavedFortress(
        UUID owner,
        int slot,
        String name,
        Blueprint blueprint,
        boolean playable,
        boolean isDefault) {

    public int size() {
        return blueprint.size();
    }

    public int blockCount() {
        return blueprint.blockCount();
    }

    public SavedFortress asDefault(boolean value) {
        return new SavedFortress(owner, slot, name, blueprint, playable, value);
    }

    public SavedFortress asPlayable(boolean value) {
        return new SavedFortress(owner, slot, name, blueprint, value, isDefault);
    }
}

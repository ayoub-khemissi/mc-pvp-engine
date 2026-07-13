package fr.ayoub.pvp.domain.mode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which game modes exist, in which order, and which of them are on.
 *
 * This is what the compass menu shows. The order is the one each mode <b>declares</b>, not
 * the order its plugin happened to load in — Bukkit loads plugins alphabetically, and a
 * menu whose layout depends on that is a menu that reshuffles itself the day you rename a
 * jar.
 *
 * Pure: no Bukkit, no plugin, so the whole ordering rule is unit-tested with no server.
 */
public final class ModeCatalog {

    private final Map<String, ModeSlot> slots = new HashMap<>();

    /** @throws IllegalStateException if that id is already taken */
    public void register(ModeSlot slot) {
        if (slots.containsKey(slot.id())) {
            throw new IllegalStateException("a game mode with id '" + slot.id() + "' is already registered");
        }
        slots.put(slot.id(), slot);
    }

    /** What the player sees, in the order they see it: the modes that are on, by rank. */
    public List<ModeSlot> active() {
        return slots.values().stream()
                .filter(ModeSlot::enabled)
                .sorted(ModeSlot.BY_RANK)
                .toList();
    }

    /** Everything installed, on or off — for the admin readout. */
    public List<ModeSlot> all() {
        return new ArrayList<>(slots.values().stream().sorted(ModeSlot.BY_RANK).toList());
    }

    public Optional<ModeSlot> find(String id) {
        return Optional.ofNullable(slots.get(id));
    }

    public boolean isEnabled(String id) {
        return find(id).map(ModeSlot::enabled).orElse(false);
    }

    /** @return false if no such mode is installed */
    public boolean setEnabled(String id, boolean enabled) {
        ModeSlot slot = slots.get(id);
        if (slot == null) {
            return false;
        }
        slots.put(id, slot.enabled(enabled));
        return true;
    }
}

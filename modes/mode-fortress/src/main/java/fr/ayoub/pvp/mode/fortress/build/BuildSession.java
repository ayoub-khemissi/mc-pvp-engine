package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.domain.fortress.Blueprint;

import java.util.UUID;

/**
 * Someone is in a build zone, editing one of their slots.
 *
 * The {@link Blueprint} is kept <b>live</b>, updated on every block placed and broken. Two
 * reasons, and both matter: a quota check ("do you have obsidian left?") is then a map
 * lookup rather than a scan of 8000 blocks on the main thread, and saving is instant
 * because the answer is already in memory.
 */
public final class BuildSession {

    private final UUID builder;
    private final BuildZone zone;
    private final int slot;

    private String name;
    private Blueprint blueprint;
    private boolean dirty;

    public BuildSession(UUID builder, BuildZone zone, int slot, String name, Blueprint blueprint) {
        this.builder = builder;
        this.zone = zone;
        this.slot = slot;
        this.name = name;
        this.blueprint = blueprint;
    }

    public UUID builder() {
        return builder;
    }

    public BuildZone zone() {
        return zone;
    }

    public int slot() {
        return slot;
    }

    public String name() {
        return name;
    }

    public void name(String value) {
        this.name = value;
        this.dirty = true;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    /**
     * The save re-read the cube from the world; take that as the new truth.
     *
     * The in-memory blueprint is a budget tracker, and a tracker drifts: a door places two
     * blocks and only reports one. Re-syncing it here is what stops the "obsidian left"
     * counter from lying for the rest of the session.
     */
    public void replaceBlueprint(Blueprint scanned) {
        this.blueprint = scanned;
    }

    /** Something changed since the last save. Leaving now would lose it. */
    public boolean dirty() {
        return dirty;
    }

    public void touch() {
        dirty = true;
    }

    public void saved() {
        dirty = false;
    }
}

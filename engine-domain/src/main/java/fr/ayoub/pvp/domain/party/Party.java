package fr.ayoub.pvp.domain.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A group of friends who queue together.
 *
 * The member list is ordered and <b>the leader is always first</b>. That single invariant
 * is what makes succession trivial: when the leader leaves, the next in line inherits the
 * party, so a group never dies just because one person disconnected.
 *
 * Pure — the Bukkit layer only turns these UUIDs into players and messages.
 */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private final List<UUID> members = new ArrayList<>();
    private final int maxSize;

    public Party(UUID leader, int maxSize) {
        Objects.requireNonNull(leader, "leader");
        if (maxSize < 1) {
            throw new IllegalArgumentException("a party holds at least one player, got " + maxSize);
        }
        this.maxSize = maxSize;
        this.members.add(leader);
    }

    public UUID id() {
        return id;
    }

    /** The leader first, then the others in the order they joined. */
    public List<UUID> members() {
        return List.copyOf(members);
    }

    public UUID leader() {
        return members.getFirst();
    }

    public boolean isLeader(UUID player) {
        return !members.isEmpty() && members.getFirst().equals(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public int size() {
        return members.size();
    }

    public int maxSize() {
        return maxSize;
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public void add(UUID player) {
        if (members.contains(player)) {
            return;
        }
        if (isFull()) {
            throw new IllegalStateException("the party is full (" + maxSize + ")");
        }
        members.add(player);
    }

    /** Removing the leader promotes the next member — the party survives. */
    public void remove(UUID player) {
        members.remove(player);
    }
}

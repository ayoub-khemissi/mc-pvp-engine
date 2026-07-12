package fr.ayoub.pvp.api;

import java.util.List;
import java.util.UUID;

/** One side of a match. */
public record Team(int index, List<UUID> members) {

    public Team {
        members = List.copyOf(members);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }
}

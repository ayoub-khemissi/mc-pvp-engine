package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.domain.arena.ArenaSelector;
import fr.ayoub.pvp.domain.arena.MapDescriptor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the arenas and knows who is inside which one.
 *
 * Allocation is "static slots": arenas are pre-built and simply borrowed for a match.
 * That is enough for PvP, where nobody breaks blocks — no world copying, no disk I/O.
 * A world-instancing provider can replace this later behind the same methods.
 */
public final class ArenaService {

    private final Map<String, Arena> arenas = new HashMap<>();
    private final Set<String> busy = new HashSet<>();
    private final Map<UUID, Arena> playerArena = new HashMap<>();

    public void load(List<Arena> loaded) {
        arenas.clear();
        busy.clear();
        for (Arena arena : loaded) {
            arenas.put(arena.id(), arena);
        }
    }

    public List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    public Optional<Arena> byId(String id) {
        return Optional.ofNullable(arenas.get(id));
    }

    /**
     * Borrow a free arena for a match.
     *
     * The map is chosen by mode and by the players' rating (Clash-Royale style) —
     * see the unit-tested {@link ArenaSelector}.
     */
    public Optional<Arena> allocate(GameModeDefinition mode, int averageRating) {
        List<Arena> free = arenas.values().stream()
                .filter(arena -> !busy.contains(arena.id()))
                .toList();

        List<MapDescriptor> descriptors = free.stream().map(Arena::descriptor).toList();

        return ArenaSelector.select(descriptors, mode.id(), averageRating, mode.requiresDedicatedMap())
                .flatMap(chosen -> free.stream()
                        .filter(arena -> arena.id().equals(chosen.id()))
                        .findFirst())
                .map(arena -> {
                    busy.add(arena.id());
                    return arena;
                });
    }

    /**
     * Is there a map for this mode at all — busy or not?
     *
     * Asked <b>before</b> anybody is queued. A mode with no map is not "waiting for a free
     * arena", it is unplayable, and a player who is told so can go and do something else
     * instead of standing in a queue that will never move.
     */
    public boolean hasMapFor(GameModeDefinition mode) {
        return arenas.values().stream()
                .map(Arena::descriptor)
                .anyMatch(map -> mode.requiresDedicatedMap()
                        ? map.isDedicatedTo(mode.id())
                        : map.supports(mode.id()));
    }

    public void release(Arena arena) {
        busy.remove(arena.id());
    }

    public boolean isBusy(Arena arena) {
        return busy.contains(arena.id());
    }

    // --- who is inside ---------------------------------------------------------

    public void enter(Player player, Arena arena) {
        playerArena.put(player.getUniqueId(), arena);
    }

    public void leave(Player player) {
        playerArena.remove(player.getUniqueId());
    }

    public Optional<Arena> arenaOf(Player player) {
        return Optional.ofNullable(playerArena.get(player.getUniqueId()));
    }

    public boolean isInArena(Player player) {
        return playerArena.containsKey(player.getUniqueId());
    }
}

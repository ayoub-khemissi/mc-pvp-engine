package fr.ayoub.pvp.mode.fortress.match;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which crystal belongs to which running match.
 *
 * A Bukkit listener is registered once, for the whole server, and is handed an entity. A
 * {@link FortressHandler} exists once <b>per match</b>. This is the join between them: given
 * the crystal that was hit, whose match is it?
 *
 * Also the guard rail: a crystal that is not in here is not part of a live match, so it is
 * not a target — it is scenery, or a leftover, and it is protected rather than blown up.
 */
public final class CrystalRegistry {

    private record Entry(FortressHandler handler, Crystal crystal) {
    }

    private final Map<UUID, Entry> byEntity = new HashMap<>();

    void register(FortressHandler handler, Crystal crystal) {
        byEntity.put(crystal.entity().getUniqueId(), new Entry(handler, crystal));
    }

    void forget(Crystal crystal) {
        byEntity.remove(crystal.entity().getUniqueId());
    }

    Optional<Entry> find(UUID entity) {
        return Optional.ofNullable(byEntity.get(entity));
    }

    /** Is this entity a crystal that a live match is being won on? */
    public boolean isMatchCrystal(UUID entity) {
        return byEntity.containsKey(entity);
    }

    void hit(UUID entity, int damage, org.bukkit.entity.Player attacker) {
        find(entity).ifPresent(found -> found.handler().onCrystalHit(found.crystal(), damage, attacker));
    }

    /** Somebody is digging out the block a crystal stands on. */
    void baseBroken(org.bukkit.block.Block block, org.bukkit.entity.Player attacker) {
        byEntity.values().stream()
                .filter(entry -> entry.crystal().isBase(block))
                .findFirst()
                .ifPresent(entry -> entry.handler().onBaseBroken(entry.crystal(), attacker));
    }
}

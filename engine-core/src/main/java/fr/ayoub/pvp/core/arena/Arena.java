package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.arena.MapDescriptor;
import fr.ayoub.pvp.domain.region.Region;
import fr.ayoub.pvp.domain.region.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A playable area: where each team spawns, the invisible wall around it, and what it
 * is for ({@link MapDescriptor}: which modes, which rating band).
 */
public record Arena(MapDescriptor descriptor, World world, List<Location> spawns,
                    Region bounds, Map<String, Location> markers, Render render) {

    /** How far outside the bounds litter still counts as ours (an arrow in the wall). */
    private static final double LITTER_MARGIN = 3.0;

    /**
     * How far this map has to be rendered and ticked, in chunks. 0 = "whatever the engine
     * defaults to".
     *
     * <p>The map is the only thing that knows. A duel arena is 48 blocks wide and walled in:
     * rendering 300 blocks of void around it is pure waste, and waste multiplied by the
     * number of concurrent matches is what decides how many fit on the box. A Fortress island
     * is 128 blocks, and a player standing at their own gate must be able to SEE the enemy
     * fortress — render it short and they get fog where the target should be.
     *
     * <p>So the number lives in {@code map.yml}, written by whoever built the map. The engine
     * takes the largest one per world and never has to know which mode asked for it.
     */
    public record Render(int viewDistance, int simulationDistance) {
        public static final Render DEFAULT = new Render(0, 0);
    }

    public Arena {
        spawns = List.copyOf(spawns);
        markers = Map.copyOf(markers);
    }

    /**
     * A point the map named, which the engine does not understand.
     *
     * "fortress-pad-0", "payload-start". The engine carries them and hands them to whichever
     * mode asked — it never learns what they mean, which is exactly why a new mode does not
     * need a new engine.
     */
    public Optional<Location> marker(String name) {
        Location at = markers.get(name);
        return Optional.ofNullable(at == null ? null : at.clone());
    }

    public String id() {
        return descriptor.id();
    }

    public int teamCount() {
        return spawns.size();
    }

    public Location spawn(int team) {
        return spawns.get(Math.floorMod(team, spawns.size())).clone();
    }

    public Location center() {
        return toLocation(bounds.center());
    }

    public boolean contains(Location location) {
        return location.getWorld().equals(world) && bounds.contains(toVec(location));
    }

    /**
     * Sweep away everything a round leaves lying around: arrows planted in the floor,
     * dropped items, thrown potions, XP orbs.
     *
     * Arenas share one world, so this must only touch its own: an entity counts as ours
     * when it is inside the bounds — or within {@link #LITTER_MARGIN} of them, because an
     * arrow buried in the floor or stuck in the wall sits a hair outside the region.
     *
     * @return how many entities were removed
     */
    public int clearLitter() {
        int removed = 0;

        for (Entity entity : world.getEntities()) {
            if (!isLitter(entity) || !isNear(entity.getLocation())) {
                continue;
            }
            entity.remove();
            removed++;
        }
        return removed;
    }

    /** Anything a fight can leave behind. Players and their vehicles are not litter. */
    private static boolean isLitter(Entity entity) {
        return entity instanceof Item
                || entity instanceof Projectile
                || entity instanceof ExperienceOrb
                || entity instanceof AreaEffectCloud
                || entity instanceof Firework
                || entity instanceof TNTPrimed;
    }

    private boolean isNear(Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }
        Vec3 at = toVec(location);
        return bounds.nearestInside(at).distance(at) <= LITTER_MARGIN;
    }

    public Location toLocation(Vec3 point) {
        return new Location(world, point.x(), point.y(), point.z());
    }

    public static Vec3 toVec(Location location) {
        return new Vec3(location.getX(), location.getY(), location.getZ());
    }
}

package fr.ayoub.pvp.core.arena;

import fr.ayoub.pvp.domain.arena.MapDescriptor;
import fr.ayoub.pvp.domain.region.Region;
import fr.ayoub.pvp.domain.region.Vec3;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * A playable area: where each team spawns, the invisible wall around it, and what it
 * is for ({@link MapDescriptor}: which modes, which rating band).
 */
public record Arena(MapDescriptor descriptor, World world, List<Location> spawns, Region bounds) {

    public Arena {
        spawns = List.copyOf(spawns);
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

    public Location toLocation(Vec3 point) {
        return new Location(world, point.x(), point.y(), point.z());
    }

    public static Vec3 toVec(Location location) {
        return new Vec3(location.getX(), location.getY(), location.getZ());
    }
}

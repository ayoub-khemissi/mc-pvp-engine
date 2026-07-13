package fr.ayoub.pvp.mode.fortress.build;

import fr.ayoub.pvp.domain.fortress.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * One builder's room, in the world.
 *
 * A thin skin over {@link ZoneGeometry}: this class knows about Bukkit, the geometry knows
 * about arithmetic, and only one of the two needs a server to be trusted.
 */
public record BuildZone(ZoneGeometry geometry, World world) {

    public int index() {
        return geometry.index();
    }

    public int cubeSize() {
        return geometry.cubeSize();
    }

    public Location toWorld(BlockPos pos) {
        int[] at = geometry.toWorld(pos);
        return new Location(world, at[0], at[1], at[2]);
    }

    public BlockPos toBlueprint(Block block) {
        return geometry.toBlueprint(block.getX(), block.getY(), block.getZ());
    }

    public boolean isInCube(Block block) {
        return geometry.isInCube(block.getX(), block.getY(), block.getZ());
    }

    public boolean isInRoom(Location location) {
        return world.equals(location.getWorld())
                && geometry.isInRoom(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Where the builder lands: on the floor, in a corner, looking at their fortress. */
    public Location spawn() {
        Location spawn = new Location(world,
                geometry.roomX() + 5.5,
                geometry.roomY() + 1,
                geometry.roomZ() + 5.5);
        spawn.setDirection(center().toVector().subtract(spawn.toVector()));
        return spawn;
    }

    public Location center() {
        return new Location(world,
                geometry.cubeX() + cubeSize() / 2.0,
                geometry.cubeY() + cubeSize() / 2.0,
                geometry.cubeZ() + cubeSize() / 2.0);
    }
}

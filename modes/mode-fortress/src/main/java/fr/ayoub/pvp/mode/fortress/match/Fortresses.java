package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.domain.fortress.BlockIds;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.CubeRotation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;

/**
 * Putting a fortress into the world, and taking it out again.
 *
 * Used twice, and the second use is the reason this is not a private method: the same paste
 * that stands a fortress on its pad also stands the three candidates up on the voting plain.
 * Two copies of it would drift, and the one that drifted would be the one nobody plays on
 * every day.
 */
public final class Fortresses {

    private Fortresses() {
    }

    /**
     * @param turned team 1's fortress faces the other way. A blueprint's front is its z = 0
     *               face and the pads face each other down the map: pasted unrotated, the
     *               second team defends a fortress whose gate opens away from the enemy.
     *               The candidates on a voting plain are all shown the same way up.
     */
    public static void paste(MatchContext context, Blueprint blueprint,
                             int x, int y, int z, boolean turned) {

        CubeRotation rotation = turned ? CubeRotation.HALF_TURN : CubeRotation.NONE;
        StructureRotation blocks = turned
                ? StructureRotation.CLOCKWISE_180
                : StructureRotation.NONE;

        int size = blueprint.size();

        for (int by = 0; by < size; by++) {
            for (int bz = 0; bz < size; bz++) {
                for (int bx = 0; bx < size; bx++) {
                    BlockPos from = new BlockPos(bx, by, bz);
                    if (blueprint.isAir(from)) {
                        continue;
                    }
                    BlockPos to = rotation.apply(from, size);

                    Block block = context.world().getBlockAt(x + to.x(), y + to.y(), z + to.z());

                    place(block, blueprint.get(from), blocks);
                }
            }
        }
    }

    private static void place(Block block, String state, StructureRotation rotation) {
        try {
            var data = Bukkit.createBlockData(state);
            data.rotate(rotation);
            block.setBlockData(data, false);
            return;
        } catch (IllegalArgumentException e) {
            // The saved state did not survive a version change. A stair facing the wrong way
            // is a nuisance; a hole in a fortress is a lost match.
        }

        Material material = Material.matchMaterial(BlockIds.typeOf(state));
        if (material != null && material.isBlock()) {
            block.setType(material, false);
        }
    }

    /** Empty a cube. Used to take the voting displays back down. */
    public static void clear(World world, int x, int y, int z, int size) {
        for (int by = 0; by < size; by++) {
            for (int bz = 0; bz < size; bz++) {
                for (int bx = 0; bx < size; bx++) {
                    world.getBlockAt(x + bx, y + by, z + bz).setType(Material.AIR, false);
                }
            }
        }
    }
}

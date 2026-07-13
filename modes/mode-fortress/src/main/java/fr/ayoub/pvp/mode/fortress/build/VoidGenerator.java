package fr.ayoub.pvp.mode.fortress.build;

import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * The build world generates nothing at all.
 *
 * The only blocks that exist are the ones we place: the rooms. A builder in an empty void
 * cannot wander into someone else's terrain, and the world costs nothing on disk.
 */
public final class VoidGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo world, Random random, int chunkX, int chunkZ, ChunkData chunk) {
        // nothing
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo world) {
        return new BiomeProvider() {
            @Override
            public org.bukkit.block.Biome getBiome(WorldInfo info, int x, int y, int z) {
                return org.bukkit.block.Biome.PLAINS;
            }

            @Override
            public List<org.bukkit.block.Biome> getBiomes(WorldInfo info) {
                return List.of(org.bukkit.block.Biome.PLAINS);
            }
        };
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}

package fr.ayoub.pvp.core.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * A world made of nothing.
 *
 * No stone, no caves, no ores, no trees, no structures — the cheapest world a server
 * can have. The only blocks that will ever exist are the ones we place ourselves
 * (the lobby platform and the arena floors).
 */
public final class VoidChunkGenerator extends ChunkGenerator {

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
}

package fr.ayoub.pvp.core.world;

import fr.ayoub.pvp.core.arena.Arena;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * How far a PvP world is rendered and ticked — the most expensive number in the whole engine,
 * and the one nobody thinks to set.
 *
 * <p>The server's global {@code view-distance} is written for a survival world, where a player
 * wants to see a mountain range. In an arena there is nothing to see: a duel is fought inside
 * 48 walled-in blocks, and past the barrier there is void. But the server does not know that.
 * Every player <b>forces the chunks around them to load</b>, and at a view-distance of 10 that
 * is 21 × 21 = <b>441 chunks per player</b> — to render nine chunks of arena.
 *
 * <p>Multiply by a hundred concurrent matches and the server is holding tens of thousands of
 * chunks nobody will ever look at. Cut the distance down to the size of the map and the same
 * hundred matches cost a fraction of the memory and a fraction of the chunk ticking. Nothing
 * about the game changes: the player already could not see past the wall.
 *
 * <p><b>The map decides, not the engine.</b> Each {@code map.yml} may carry its own
 * {@code view-distance} ({@link Arena.Render}), because only the map knows how big it is — a
 * Fortress island is 128 blocks and a player must be able to see the enemy fortress across it,
 * while a duel arena is 48 and does not. The engine takes the <b>largest</b> requirement among
 * the maps sharing a world, falls back to the config default, and never has to know which mode
 * asked for what. A designer's map will declare its own the same way.
 */
public final class WorldTuning {

    private WorldTuning() {
    }

    /**
     * @param lobby  the lobby world — small, and tuned to the default
     * @param arenas every map the engine loaded, each of which knows how far it needs to see
     */
    public static void apply(Plugin plugin, World lobby, Collection<Arena> arenas) {
        int defaultView = plugin.getConfig().getInt("world.view-distance", 5);
        int defaultSimulation = plugin.getConfig().getInt("world.simulation-distance", 4);

        if (defaultView <= 0 && defaultSimulation <= 0) {
            return;   // 0 = leave the server's own settings alone
        }

        Map<World, int[]> needed = new HashMap<>();
        needed.put(lobby, new int[]{defaultView, defaultSimulation});

        for (Arena arena : arenas) {
            int[] world = needed.computeIfAbsent(arena.world(),
                    key -> new int[]{defaultView, defaultSimulation});

            // The biggest map in a world wins: rendering short enough for one map and too
            // short for another is how you get fog in the middle of somebody's arena.
            world[0] = Math.max(world[0], arena.render().viewDistance());
            world[1] = Math.max(world[1], arena.render().simulationDistance());
        }

        for (Map.Entry<World, int[]> entry : needed.entrySet()) {
            World world = entry.getKey();
            int view = entry.getValue()[0];
            int simulation = Math.min(entry.getValue()[1], view);   // never tick past sight

            if (view > 0) {
                world.setViewDistance(view);
                world.setSendViewDistance(view);
            }
            if (simulation > 0) {
                world.setSimulationDistance(simulation);
            }

            plugin.getLogger().info("World '" + world.getName() + "': view-distance " + view
                    + ", simulation-distance " + simulation);
        }
    }
}

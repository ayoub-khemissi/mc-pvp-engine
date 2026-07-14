package fr.ayoub.pvp.core.world;

import fr.ayoub.pvp.core.arena.Arena;
import org.bukkit.GameRule;
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
        boolean locatorBar = plugin.getConfig().getBoolean("world.locator-bar", false);

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
            hideTheLocatorBar(world, locatorBar);

            // Read BACK, not the value we asked for. setGameRule can refuse, and a log line that
            // reports our own intent would happily say "off" over a server still broadcasting
            // every player's position.
            plugin.getLogger().info("World '" + world.getName() + "': view-distance " + view
                    + ", simulation-distance " + simulation
                    + ", locator-bar " + world.getGameRuleValue(GameRule.LOCATOR_BAR));
        }
    }

    /**
     * The strip of dots above the hotbar that tells you where every other player is.
     *
     * <p>Minecraft added it in 1.21.6 and it is <b>on by default</b>, which quietly hands every
     * PvP server a wallhack: a Fortress attacker knows exactly where the defenders are standing
     * inside a fortress they cannot see into, and a duellist tracks an opponent through a pillar.
     * It has nothing to do with line of sight — waypoints are broadcast across the whole
     * dimension, far past the view distance, and the server's waypoint manager never consults
     * Bukkit at all (hiding the <em>entity</em> with {@code hidePlayer} does nothing to the dot).
     *
     * <p>So it goes off, in every world the engine owns. The gamerule is the only lever Paper
     * gives us and it is <b>per world</b>: there is no way to show a player their team-mates and
     * hide their enemies, because there is no API to filter a waypoint by who is receiving it.
     * Off for everyone is the only honest answer that does not reach into the server's internals.
     */
    private static void hideTheLocatorBar(World world, boolean enabled) {
        world.setGameRule(GameRule.LOCATOR_BAR, enabled);
    }
}

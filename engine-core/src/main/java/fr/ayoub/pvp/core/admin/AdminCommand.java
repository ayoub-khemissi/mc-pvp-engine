package fr.ayoub.pvp.core.admin;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.arena.Arena;
import fr.ayoub.pvp.core.arena.ArenaLoader;
import fr.ayoub.pvp.core.arena.ArenaService;
import fr.ayoub.pvp.core.world.WorldSetup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Admin only. Players never type commands — but staff need to inspect and test maps.
 *
 *   /pvpadmin arena list
 *   /pvpadmin arena tp <id>     enter an arena (walls become active)
 *   /pvpadmin arena leave       back to the lobby
 *   /pvpadmin reload            reload the map files
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final PvPEnginePlugin plugin;
    private final ArenaService arenas;

    public AdminCommand(PvPEnginePlugin plugin, ArenaService arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                arenas.load(ArenaLoader.loadAll(plugin));
                send(sender, "Reloaded " + arenas.all().size() + " map(s).", NamedTextColor.GREEN);
            }
            case "setup" -> setup(sender, args);
            case "arena" -> arena(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    /**
     * Builds the test map: a lobby and N arenas in the void world.
     * N arenas = N matches can run at the same time.
     */
    private void setup(CommandSender sender, String[] args) {
        int count = 4;
        if (args.length > 1) {
            try {
                count = Math.max(1, Math.min(16, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                send(sender, "Usage: /pvpadmin setup [arenas]", NamedTextColor.RED);
                return;
            }
        }

        String worldName = plugin.getConfig().getString("world.name", "pvp");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            send(sender, "The world '" + worldName + "' is not loaded.", NamedTextColor.RED);
            return;
        }

        send(sender, "Building the lobby and " + count + " arena(s)…", NamedTextColor.YELLOW);
        WorldSetup.Result result = WorldSetup.build(plugin, world, count);

        // Point the lobby at the platform we just built.
        plugin.getConfig().set("lobby.world", world.getName());
        plugin.getConfig().set("lobby.x", 0.5);
        plugin.getConfig().set("lobby.y", (double) WorldSetup.LOBBY_Y + 1);
        plugin.getConfig().set("lobby.z", 0.5);
        plugin.getConfig().set("lobby.yaw", 0.0);
        plugin.getConfig().set("lobby.pitch", 0.0);
        plugin.saveConfig();

        arenas.load(ArenaLoader.loadAll(plugin));

        send(sender, "Done: " + result.blocksPlaced() + " blocks, "
                + result.arenas() + " arena(s) → " + result.arenas() + " simultaneous matches.",
                NamedTextColor.GREEN);
        send(sender, "Restart the server (or /reload) so the lobby spawn is picked up.",
                NamedTextColor.GRAY);
    }

    private void arena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> {
                List<Arena> all = arenas.all();
                if (all.isEmpty()) {
                    send(sender, "No maps loaded. Put a .yml in plugins/PvPEngine/maps/.", NamedTextColor.YELLOW);
                    return;
                }
                send(sender, "Maps (" + all.size() + "):", NamedTextColor.GOLD);
                for (Arena arena : all) {
                    send(sender, " - " + arena.id()
                            + "  world=" + arena.world().getName()
                            + "  teams=" + arena.teamCount()
                            + (arenas.isBusy(arena) ? "  [in use]" : ""), NamedTextColor.GRAY);
                }
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "Only a player can do that.", NamedTextColor.RED);
                    return;
                }
                if (args.length < 3) {
                    send(sender, "Usage: /pvpadmin arena tp <id>", NamedTextColor.RED);
                    return;
                }
                Optional<Arena> found = arenas.byId(args[2]);
                if (found.isEmpty()) {
                    send(sender, "No map called '" + args[2] + "'.", NamedTextColor.RED);
                    return;
                }
                Arena arena = found.get();
                arenas.enter(player, arena);
                player.teleport(arena.spawn(0));
                send(sender, "Entered '" + arena.id() + "'. The walls are active — try to walk out.",
                        NamedTextColor.GREEN);
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "Only a player can do that.", NamedTextColor.RED);
                    return;
                }
                arenas.leave(player);
                plugin.lobby().send(player);
                send(sender, "Back to the lobby.", NamedTextColor.GREEN);
            }
            default -> usage(sender);
        }
    }

    private void usage(CommandSender sender) {
        send(sender, "/pvpadmin setup [arenas] | arena list | arena tp <id> | arena leave | reload",
                NamedTextColor.YELLOW);
    }

    private void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text("[PvP] ", NamedTextColor.DARK_AQUA)
                .append(Component.text(message, color)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("setup");
            out.add("arena");
            out.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            out.add("list");
            out.add("tp");
            out.add("leave");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("tp")) {
            arenas.all().forEach(arena -> out.add(arena.id()));
        }
        return out;
    }
}

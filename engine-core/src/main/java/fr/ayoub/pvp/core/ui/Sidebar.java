package fr.ayoub.pvp.core.ui;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** The sidebar: who you are, and what you are currently doing. */
public final class Sidebar {

    private Sidebar() {
    }

    public static void update(PvPEnginePlugin plugin, Player player) {
        Scoreboard board = player.getScoreboard();

        // Give the player their own board the first time.
        if (board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective("pvp");
        if (objective == null) {
            objective = board.registerNewObjective(
                    "pvp", Criteria.DUMMY, Component.text("PVP", NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Scoreboards cannot be edited in place — wipe and rewrite.
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        Objective lines = objective;
        lines.getScore("§7Player: §f" + player.getName()).setScore(4);
        lines.getScore("§7State: " + state(plugin, player)).setScore(3);

        plugin.parties().partyOf(player).ifPresent(party ->
                lines.getScore("§7Party: §d" + party.size() + "/" + party.maxSize()).setScore(2));

        lines.getScore("§7Online: §f" + Bukkit.getOnlinePlayers().size()).setScore(1);
    }

    private static String state(PvPEnginePlugin plugin, Player player) {
        if (plugin.matches().isSpectating(player)) {
            return "§bSpectating";
        }
        if (plugin.matches().isInMatch(player)) {
            return plugin.matches().matchOf(player)
                    .map(match -> "§cRound " + match.round() + "/" + match.bestOf()
                            + " §7(" + match.series().scoreline() + ")")
                    .orElse("§cIn match");
        }
        if (plugin.queue().isQueued(player)) {
            String name = plugin.queue().queueNameOf(player).orElse("?");
            return "§eQueued §7(" + name + ", " + plugin.queue().waitedSeconds(player) + "s)";
        }
        return "§aLobby";
    }

    public static void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}

package fr.ayoub.pvp.core.ui;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.match.Match;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar: who you are, and what you are currently doing.
 *
 * <p><b>The clock lives here, and only here.</b> It used to be drawn by the mode, on every boss
 * bar the mode owned — which in Fortress meant two crystal bars, each ending in the same
 * countdown, so the player was told the time twice.
 *
 * <p>The engine is what <b>owns</b> the clock: it starts it, it ends the match on it, and
 * {@code MatchContext.secondsLeft()} is how anyone reads it. So the engine is what draws it. A
 * mode's HUD is then free to be about the mode — crystals, kills, a payload — and never about
 * the time, the round or the scoreline, because those belong to the engine and it is already
 * showing them. One fact, one place, and the next mode inherits it without writing a line.
 */
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

        // Written top-down; the score counts down. Nobody has to juggle line numbers by hand
        // again, which is what made adding a line to this thing a small ordeal.
        List<String> lines = linesFor(plugin, player);
        int score = lines.size();

        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
    }

    private static List<String> linesFor(PvPEnginePlugin plugin, Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("§7Player: §f" + player.getName());

        Match match = plugin.matches().matchOf(player).orElse(null);

        if (match != null) {
            lines.add("§7State: " + (match.isAlive(player) ? "§cFighting" : "§bEliminated"));

            // Only a best-of has rounds to speak of. "Round 1/1" over a thirty-minute Fortress
            // match tells the player nothing, and takes the room from a line that would.
            if (match.bestOf() > 1) {
                lines.add("§7Round: §f" + match.round() + "/" + match.bestOf()
                        + " §7(" + match.series().scoreline() + ")");
            }

            // THE clock. Singular. See the class comment.
            if (match.mode().rules().hasTimeLimit()) {
                lines.add("§7Time: §e" + clock(match.secondsLeft()));
            }

        } else if (plugin.matches().isSpectating(player)) {
            lines.add("§7State: §bSpectating");

        } else if (plugin.queue().isQueued(player)) {
            lines.add("§7State: §eQueued");
            lines.add("§7Queue: §f" + plugin.queue().queueNameOf(player).orElse("?")
                    + " §7(" + plugin.queue().waitedSeconds(player) + "s)");

        } else {
            lines.add("§7State: §aLobby");
        }

        plugin.parties().partyOf(player).ifPresent(party ->
                lines.add("§7Party: §d" + party.size() + "/" + party.maxSize()));

        lines.add("§7Online: §f" + Bukkit.getOnlinePlayers().size());
        return lines;
    }

    /** m:ss. */
    private static String clock(int seconds) {
        int left = Math.max(0, seconds);
        return String.format("%d:%02d", left / 60, left % 60);
    }

    public static void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}

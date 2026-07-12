package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchOutcome;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.arena.Arena;
import fr.ayoub.pvp.core.lobby.PlayerSnapshot;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.match.MatchState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs matches: teleport in, freeze, 3·2·1, fight, decide, put everyone back.
 *
 * The order of the states is enforced by MatchStateMachine (unit-tested), so a match
 * can never skip the countdown or "un-end" itself.
 */
public final class MatchService {

    private final PvPEnginePlugin plugin;
    private final RatingService ratings;

    private final Map<UUID, Match> matchByPlayer = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final List<Match> active = new ArrayList<>();

    public MatchService(PvPEnginePlugin plugin) {
        this.plugin = plugin;
        this.ratings = new RatingService(plugin);
    }

    public RatingService ratings() {
        return ratings;
    }

    public Optional<Match> matchOf(Player player) {
        return Optional.ofNullable(matchByPlayer.get(player.getUniqueId()));
    }

    public boolean isInMatch(Player player) {
        return matchByPlayer.containsKey(player.getUniqueId());
    }

    public List<Match> active() {
        return List.copyOf(active);
    }

    // --- starting --------------------------------------------------------------

    /**
     * @param averageRating decides which map the match is played on (Clash-Royale style)
     * @return empty if no compatible arena is free
     */
    public Optional<Match> start(GameModeDefinition mode, Format format,
                                 List<List<UUID>> teamMembers, int averageRating) {
        Optional<Arena> arena = plugin.arenas().allocate(mode.id(), averageRating);
        if (arena.isEmpty()) {
            return Optional.empty();
        }

        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < teamMembers.size(); i++) {
            teams.add(new Team(i, teamMembers.get(i)));
        }

        Match match = new Match(mode, format, arena.get(), teams);
        active.add(match);

        prepare(match);
        return Optional.of(match);
    }

    private void prepare(Match match) {
        match.state().transitionTo(MatchState.PREPARING);

        for (Team team : match.teams()) {
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }

                // Remember everything before the match touches it.
                snapshots.put(id, PlayerSnapshot.of(player));
                matchByPlayer.put(id, match);

                plugin.arenas().enter(player, match.arena());
                player.teleport(match.spawn(team.index()));
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.setHealth(20);
                player.setFoodLevel(20);
                player.setFireTicks(0);

                match.handler().giveKit(match, player, team.index());
            }
        }

        match.handler().onPrepare(match);
        countdown(match);
    }

    private void countdown(Match match) {
        match.state().transitionTo(MatchState.COUNTDOWN);

        int seconds = Math.max(1, match.mode().rules().countdownSeconds());

        new Runnable() {
            int left = seconds;
            BukkitTask task;

            void schedule() {
                task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 20L);
            }

            @Override
            public void run() {
                if (match.state().isFinished() || !match.isState(MatchState.COUNTDOWN)) {
                    task.cancel();
                    return;
                }

                if (left > 0) {
                    match.title(
                            Component.text(String.valueOf(left), NamedTextColor.GOLD),
                            Component.empty());
                    match.onlinePlayers().forEach(player ->
                            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f));
                    left--;
                    return;
                }

                task.cancel();
                begin(match);
            }
        }.schedule();
    }

    private void begin(Match match) {
        match.state().transitionTo(MatchState.LIVE);
        match.title(Component.text("FIGHT!", NamedTextColor.RED), Component.empty());
        match.onlinePlayers().forEach(player ->
                player.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f));
        match.handler().onStart(match);
    }

    // --- during ----------------------------------------------------------------

    public void handleDeath(Player victim, Player killer) {
        matchOf(victim).ifPresent(match -> {
            if (!match.isLive()) {
                return;
            }

            match.eliminate(victim);
            makeSpectator(victim, match);

            match.broadcast(Component.text(victim.getName(), NamedTextColor.RED)
                    .append(Component.text(" was eliminated", NamedTextColor.GRAY)));

            match.handler().onPlayerDeath(match, victim, killer);
            checkWin(match);
        });
    }

    public void handleQuit(Player player) {
        matchOf(player).ifPresent(match -> {
            match.removeCompletely(player.getUniqueId());
            matchByPlayer.remove(player.getUniqueId());
            snapshots.remove(player.getUniqueId());   // they will respawn in the lobby anyway

            match.broadcast(Component.text(player.getName() + " left the match", NamedTextColor.GRAY));

            if (match.isLive()) {
                checkWin(match);
            }
        });
    }

    private void checkWin(Match match) {
        MatchOutcome outcome = match.handler().checkWinCondition(match);

        if (outcome == null) {
            // engine default: last team standing
            List<Integer> standing = match.teamsAlive();
            if (standing.size() > 1) {
                return;
            }
            outcome = standing.isEmpty()
                    ? MatchOutcome.aborted()
                    : MatchOutcome.win(standing.getFirst(), MatchOutcome.Reason.LAST_TEAM_STANDING);
        }

        end(match, outcome);
    }

    // --- ending ----------------------------------------------------------------

    public void end(Match match, MatchOutcome outcome) {
        if (match.state().isFinished()) {
            return;
        }
        match.state().transitionTo(MatchState.ENDING);

        if (outcome.hasWinner()) {
            for (Player player : match.onlinePlayers()) {
                boolean won = match.teamOf(player)
                        .map(team -> team.index() == outcome.winningTeam())
                        .orElse(false);

                player.showTitle(net.kyori.adventure.title.Title.title(
                        won ? Component.text("VICTORY", NamedTextColor.GREEN)
                                : Component.text("DEFEAT", NamedTextColor.RED),
                        Component.empty()));
            }
        } else {
            match.title(Component.text("Match aborted", NamedTextColor.GRAY), Component.empty());
        }

        match.handler().onEnd(match, outcome);

        // Elo: loaded, computed and saved off the main thread; the "+18" comes back on it.
        ratings.applyResult(match, outcome);

        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(match), 60L);
    }

    /** Abort a match: put everyone back, free the arena. Safe from any state. */
    public void abort(Match match) {
        if (match.state().isFinished()) {
            return;
        }
        if (match.state().canTransitionTo(MatchState.ENDING)) {
            end(match, MatchOutcome.aborted());
        } else {
            cleanup(match);
        }
    }

    private void cleanup(Match match) {
        if (match.state().isFinished()) {
            return;
        }
        match.state().transitionTo(MatchState.CLEANUP);

        for (UUID id : match.allMembers()) {
            matchByPlayer.remove(id);

            Player player = Bukkit.getPlayer(id);
            PlayerSnapshot snapshot = snapshots.remove(id);
            if (player == null || !player.isOnline()) {
                continue;
            }

            plugin.arenas().leave(player);

            // The snapshot is the safety net; the lobby is the destination.
            if (snapshot != null) {
                snapshot.restore(player);
            }
            plugin.lobby().send(player);
        }

        plugin.arenas().release(match.arena());
        active.remove(match);
        match.state().transitionTo(MatchState.CLOSED);
    }

    /** Called on shutdown so nobody is left stranded in an arena. */
    public void abortAll() {
        for (Match match : List.copyOf(active)) {
            abort(match);
        }
    }

    private void makeSpectator(Player player, Match match) {
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(match.arena().center());
    }
}

package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchOutcome;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.arena.Arena;
import fr.ayoub.pvp.core.lobby.PlayerSnapshot;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.match.MatchState;
import fr.ayoub.pvp.domain.match.Series;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs matches: teleport in, freeze, 3·2·1, fight, decide the round, and either play the
 * next one or end the series.
 *
 * A match is a <b>best-of</b> (see {@link Series}, unit-tested): a round ends when one team
 * is left standing, the match ends when one team has won enough rounds. Between two rounds
 * everybody is healed, re-kitted and sent back to their spawn — the eliminated included.
 *
 * The order of the states is enforced by MatchStateMachine (also unit-tested), so a match
 * can never skip the countdown or "un-end" itself.
 */
public final class MatchService {

    /** How long the scoreline stays on screen between two rounds. */
    private static final long ROUND_BREAK_TICKS = 60L;

    private final PvPEnginePlugin plugin;
    private final RatingService ratings;

    private final Map<UUID, Match> matchByPlayer = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Match> spectating = new HashMap<>();
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

        for (UUID id : match.allMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            // Remember everything before the match touches it.
            snapshots.put(id, PlayerSnapshot.of(player));
            matchByPlayer.put(id, match);
        }

        setUpRound(match);
        match.handler().onPrepare(match);
        countdown(match);
    }

    /**
     * Put everyone back on their spawn, alive and re-equipped.
     *
     * Called before every round, so this is also what brings the players eliminated in the
     * previous round back out of spectator mode.
     */
    private void setUpRound(Match match) {
        match.resetAlive();

        for (Team team : match.teams()) {
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || match.hasRetired(id)) {
                    continue;
                }

                plugin.arenas().enter(player, match.arena());
                player.teleport(match.spawn(team.index()));
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                    player.removePotionEffect(effect.getType());
                }
                player.setHealth(20);
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setFireTicks(0);
                player.setFallDistance(0);

                match.handler().giveKit(match, player, team.index());
            }
        }
    }

    private void countdown(Match match) {
        match.state().transitionTo(MatchState.COUNTDOWN);

        int seconds = Math.max(1, match.mode().rules().countdownSeconds());
        Series series = match.series();

        if (series.bestOf() > 1) {
            match.title(
                    Component.text("Round " + series.round(), NamedTextColor.YELLOW),
                    Component.text(series.scoreline() + "  (first to " + series.roundsToWin() + ")",
                            NamedTextColor.GRAY));
        }

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
            checkRoundOver(match);
        });
    }

    public void handleQuit(Player player) {
        stopSpectating(player);

        matchOf(player).ifPresent(match -> {
            match.removeCompletely(player.getUniqueId());
            matchByPlayer.remove(player.getUniqueId());
            snapshots.remove(player.getUniqueId());   // they will respawn in the lobby anyway

            match.broadcast(Component.text(player.getName() + " left the match", NamedTextColor.GRAY));

            // A team that has nobody left forfeits the whole series, not just the round.
            List<Integer> remaining = match.teamsRemaining();
            if (remaining.size() == 1) {
                finish(match, MatchOutcome.win(remaining.getFirst(), MatchOutcome.Reason.FORFEIT));
                return;
            }
            if (remaining.isEmpty()) {
                abort(match);
                return;
            }

            if (match.isLive()) {
                checkRoundOver(match);
            }
        });
    }

    /** Is this round over — and if it is, is the whole match over? */
    private void checkRoundOver(Match match) {
        // A mode can decide the match outright (a flag captured, a score reached…).
        MatchOutcome outcome = match.handler().checkWinCondition(match);
        if (outcome != null) {
            finish(match, outcome);
            return;
        }

        List<Integer> standing = match.teamsAlive();
        if (standing.size() > 1) {
            return;   // the round goes on
        }

        if (standing.isEmpty()) {
            // Everybody died at once. Nobody wins the round, so replay it.
            endRound(match, MatchOutcome.NO_TEAM);
            return;
        }
        endRound(match, standing.getFirst());
    }

    /** @param winningTeam {@link MatchOutcome#NO_TEAM} for a double knock-out (round replayed) */
    private void endRound(Match match, int winningTeam) {
        match.state().transitionTo(MatchState.ROUND_ENDING);

        Series series = match.series();
        boolean draw = winningTeam == MatchOutcome.NO_TEAM;

        if (!draw) {
            series.recordRound(winningTeam);
            match.handler().onRoundEnd(match, winningTeam);
        }

        if (series.isDecided()) {
            finish(match, MatchOutcome.win(winningTeam, MatchOutcome.Reason.LAST_TEAM_STANDING));
            return;
        }

        // Show the scoreline, then set the next round up.
        if (draw) {
            match.title(Component.text("Draw", NamedTextColor.GRAY),
                    Component.text("The round is replayed", NamedTextColor.GRAY));
        } else {
            match.title(
                    Component.text("Round " + (series.round() - 1) + " — Team "
                            + (winningTeam + 1), NamedTextColor.GREEN),
                    Component.text(series.scoreline(), NamedTextColor.WHITE));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!match.isState(MatchState.ROUND_ENDING)) {
                return;   // aborted, or the server is shutting down
            }
            setUpRound(match);
            countdown(match);
        }, ROUND_BREAK_TICKS);
    }

    // --- ending ----------------------------------------------------------------

    /**
     * End the whole match.
     *
     * Callable from any live state; if the state machine will not allow ENDING (a quit
     * during the countdown, say) the match is simply cleaned up and nothing is rated.
     */
    private void finish(Match match, MatchOutcome outcome) {
        if (!match.state().canTransitionTo(MatchState.ENDING)) {
            cleanup(match);
            return;
        }
        end(match, outcome);
    }

    public void end(Match match, MatchOutcome outcome) {
        if (match.state().isFinished()) {
            return;
        }
        match.state().transitionTo(MatchState.ENDING);

        if (outcome.hasWinner()) {
            String score = match.series().scoreline();

            for (Player player : match.onlinePlayers()) {
                boolean won = match.teamOf(player)
                        .map(team -> team.index() == outcome.winningTeam())
                        .orElse(false);

                player.showTitle(Title.title(
                        won ? Component.text("VICTORY", NamedTextColor.GREEN)
                                : Component.text("DEFEAT", NamedTextColor.RED),
                        Component.text(score, NamedTextColor.GRAY)));
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

        // Nobody is left watching an arena that no longer has a match in it.
        for (UUID id : List.copyOf(spectating.keySet())) {
            if (spectating.get(id) != match) {
                continue;
            }
            Player spectator = Bukkit.getPlayer(id);
            if (spectator != null) {
                stopSpectating(spectator);
            } else {
                spectating.remove(id);
            }
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

    // --- spectators ------------------------------------------------------------

    public boolean isSpectating(Player player) {
        return spectating.containsKey(player.getUniqueId());
    }

    public Optional<Match> spectatedMatch(Player player) {
        return Optional.ofNullable(spectating.get(player.getUniqueId()));
    }

    /**
     * Watch a live match.
     *
     * Spectators are in Minecraft's SPECTATOR mode: they fly through the walls, cannot be
     * seen or hit, and cannot touch anything — so they can never interfere with a ranked
     * game. They are deliberately <b>not</b> registered with the arena: the server-side
     * wall check would fight them.
     */
    public void spectate(Player player, Match match) {
        if (isInMatch(player) || !active.contains(match)) {
            return;
        }

        spectating.put(player.getUniqueId(), match);
        snapshots.putIfAbsent(player.getUniqueId(), PlayerSnapshot.of(player));

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(match.arena().center());
        player.sendMessage(Component.text("Now spectating ", NamedTextColor.AQUA)
                .append(Component.text(match.mode().id() + " " + match.format().id(),
                        NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Sneak (Shift) to go back to the lobby.",
                NamedTextColor.GRAY));
    }

    public void stopSpectating(Player player) {
        if (spectating.remove(player.getUniqueId()) == null) {
            return;
        }
        snapshots.remove(player.getUniqueId());

        if (player.isOnline()) {
            plugin.lobby().send(player);   // resets the game mode and the hotbar
        }
    }

    /** Everyone watching this match right now. */
    public List<Player> spectatorsOf(Match match) {
        return spectating.entrySet().stream()
                .filter(entry -> entry.getValue() == match)
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .filter(player -> player != null && player.isOnline())
                .toList();
    }

    /**
     * A player eliminated from the round watches the rest of it — they are back in the
     * fight next round. This is not the same as {@link #spectate}: they are still very
     * much in the match.
     */
    private void makeSpectator(Player player, Match match) {
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(match.arena().center());
    }
}

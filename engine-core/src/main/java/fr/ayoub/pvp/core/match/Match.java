package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.core.arena.Arena;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.match.MatchState;
import fr.ayoub.pvp.domain.match.MatchStateMachine;
import fr.ayoub.pvp.domain.match.Series;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One running match. Also the {@link MatchContext} the game mode sees. */
public final class Match implements MatchContext {

    private static final Title.Times TIMES =
            Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(400));

    private final UUID id = UUID.randomUUID();
    private final GameModeDefinition mode;
    private final Format format;
    private final Arena arena;
    private final List<Team> teams;
    private final MatchHandler handler;
    private final MatchStateMachine state = new MatchStateMachine();

    private final Set<UUID> alive = new HashSet<>();

    /** Players who left for good. They never come back, not even next round. */
    private final Set<UUID> retired = new HashSet<>();

    private final Series series;

    public Match(GameModeDefinition mode, Format format, Arena arena, List<Team> teams) {
        this.mode = mode;
        this.format = format;
        this.arena = arena;
        this.teams = List.copyOf(teams);
        this.handler = mode.createHandler();
        this.series = new Series(mode.rules().rounds(), teams.size());
        resetAlive();
    }

    // --- engine side -----------------------------------------------------------

    public MatchStateMachine state() {
        return state;
    }

    public MatchHandler handler() {
        return handler;
    }

    public Arena arena() {
        return arena;
    }

    public boolean isLive() {
        return state.isLive();
    }

    public Series series() {
        return series;
    }

    /** Everyone who has not left the server comes back to life for the next round. */
    public void resetAlive() {
        alive.clear();
        for (Team team : teams) {
            team.members().stream()
                    .filter(member -> !retired.contains(member))
                    .forEach(alive::add);
        }
    }

    /** Teams that still have at least one player alive in the current round. */
    public List<Integer> teamsAlive() {
        List<Integer> standing = new ArrayList<>();
        for (Team team : teams) {
            if (team.members().stream().anyMatch(alive::contains)) {
                standing.add(team.index());
            }
        }
        return standing;
    }

    /** Teams that still have at least one player <b>in the match at all</b>. */
    public List<Integer> teamsRemaining() {
        List<Integer> remaining = new ArrayList<>();
        for (Team team : teams) {
            if (team.members().stream().anyMatch(member -> !retired.contains(member))) {
                remaining.add(team.index());
            }
        }
        return remaining;
    }

    public List<UUID> allMembers() {
        List<UUID> all = new ArrayList<>();
        teams.forEach(team -> all.addAll(team.members()));
        return all;
    }

    /**
     * Remove someone for good (disconnected).
     *
     * They must stay out for the rest of the series: without this, the next round would
     * happily bring a player who is no longer on the server back to life.
     */
    public void removeCompletely(UUID player) {
        alive.remove(player);
        retired.add(player);
    }

    public boolean hasRetired(UUID player) {
        return retired.contains(player);
    }

    // --- MatchContext ----------------------------------------------------------

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public GameModeDefinition mode() {
        return mode;
    }

    @Override
    public Format format() {
        return format;
    }

    @Override
    public List<Team> teams() {
        return teams;
    }

    @Override
    public Optional<Team> teamOf(Player player) {
        return teams.stream().filter(team -> team.contains(player.getUniqueId())).findFirst();
    }

    @Override
    public List<Player> alivePlayers() {
        return alive.stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .toList();
    }

    @Override
    public List<Player> onlinePlayers() {
        return allMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .toList();
    }

    @Override
    public boolean isAlive(Player player) {
        return alive.contains(player.getUniqueId());
    }

    @Override
    public void eliminate(Player player) {
        alive.remove(player.getUniqueId());
    }

    @Override
    public int round() {
        return series.round();
    }

    @Override
    public int bestOf() {
        return series.bestOf();
    }

    @Override
    public int roundsWon(int team) {
        return series.wins(team);
    }

    @Override
    public World world() {
        return arena.world();
    }

    @Override
    public Location spawn(int team) {
        return arena.spawn(team);
    }

    @Override
    public String arenaId() {
        return arena.id();
    }

    @Override
    public Optional<Location> marker(String name) {
        return arena.marker(name);
    }

    @Override
    public void broadcast(Component message) {
        onlinePlayers().forEach(player -> player.sendMessage(message));
    }

    @Override
    public void title(Component title, Component subtitle) {
        Title shown = Title.title(title, subtitle, TIMES);
        onlinePlayers().forEach(player -> player.showTitle(shown));
    }

    public boolean isState(MatchState expected) {
        return state.state() == expected;
    }
}

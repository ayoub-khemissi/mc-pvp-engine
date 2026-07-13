package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.Candidate;
import fr.ayoub.pvp.domain.fortress.TeamVote;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.map.FortressMapBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Thirty seconds, three fortresses, and a team that has to agree.
 *
 * Each team is flown up to its own plain, where its members' fortresses are standing side by
 * side with a 1, a 2 and a 3 on the floor in front of them. They are <b>spectators</b>: they
 * fly, and they walk through the walls, because you should be able to look inside the thing
 * you are about to bet a match on before you pick it.
 *
 * <p>That is also the reason they do not vote with the hotbar. In SPECTATOR, Minecraft hides
 * the hotbar and takes the 1-9 keys for its own spectator menu — the two cannot both be had.
 * So the vote is a screen, and sneaking opens it: sneak is the one key a spectator can still
 * send the server.
 *
 * <p>It ends early the moment <b>both</b> teams are done. Nobody should stand around waiting
 * out a clock that has nothing left to decide.
 */
public final class VotePhase {

    private final Plugin plugin;
    private final FortressConfig config;
    private final MatchContext context;
    private final VoteRegistry registry;

    private final Map<Integer, TeamVote> votes = new HashMap<>();
    private final Map<Integer, List<Blueprint>> shown = new HashMap<>();

    private BukkitTask ticker;
    private int secondsLeft;
    private boolean over;

    /** Called with the fortress each team ended up with. */
    private final BiConsumer<Integer, Candidate> onDecided;

    public VotePhase(Plugin plugin, FortressConfig config, MatchContext context,
                     VoteRegistry registry, BiConsumer<Integer, Candidate> onDecided) {
        this.plugin = plugin;
        this.config = config;
        this.context = context;
        this.registry = registry;
        this.onDecided = onDecided;
    }

    /**
     * @param candidates per team, already ordered best-rated player first — which is what
     *                   settles a tie (see {@link TeamVote}). At least one per team: a team
     *                   with nothing to show is given a preset by the caller.
     * @param previews   the blueprints behind those candidates, in the same order
     * @param done       run when the vote is over and the fortresses are chosen
     */
    public void start(Map<Integer, List<Candidate>> candidates,
                      Map<Integer, List<Blueprint>> previews,
                      Runnable done) {

        this.secondsLeft = config.voteSeconds();

        for (Team team : context.teams()) {
            List<Candidate> options = candidates.getOrDefault(team.index(), List.of());
            if (options.isEmpty()) {
                continue;
            }

            votes.put(team.index(), new TeamVote(options));
            shown.put(team.index(), previews.getOrDefault(team.index(), List.of()));

            showFortresses(team.index());
            sendUp(team);
        }

        registry.open(this);

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (over) {
                return;
            }
            if (everyoneHasVoted() || --secondsLeft <= 0) {
                close(done);
                return;
            }
            updateActionBars();
        }, 20L, 20L);

        updateActionBars();
    }

    // --- the plain --------------------------------------------------------------------

    private void showFortresses(int team) {
        int index = FortressMapBuilder.indexOf(context.arenaId());
        if (index < 0) {
            return;
        }

        int cube = config.fortressSize();
        int ox = index * FortressMapBuilder.SPACING;
        List<Blueprint> previews = shown.get(team);

        for (int slot = 0; slot < previews.size() && slot < 3; slot++) {
            int px = FortressMapBuilder.voteSlotX(ox, cube, slot);
            int pz = FortressMapBuilder.votePlainZ(0, team);

            Fortresses.paste(context, previews.get(slot),
                    px, FortressMapBuilder.VOTE_Y + 1, pz, false);
        }
    }

    private void sendUp(Team team) {
        int index = FortressMapBuilder.indexOf(context.arenaId());
        if (index < 0) {
            return;
        }

        for (Player player : context.onlinePlayers()) {
            if (!team.contains(player.getUniqueId())) {
                continue;
            }

            // Spectator, and unfrozen: they are meant to fly through those walls.
            context.freeze(player, false);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(FortressMapBuilder.voteSpawn(
                    context.world(), index, config.fortressSize(), team.index()));

            player.sendMessage(Component.text("Choose your fortress.", NamedTextColor.GOLD));
            player.sendMessage(Component.text(
                    "Fly through them and look inside. Double-Shift to vote.",
                    NamedTextColor.GRAY));
        }
    }

    // --- voting ------------------------------------------------------------------------

    /** Sneak opened the screen; this is the click on it. */
    void cast(Player voter, int number) {
        teamOf(voter).ifPresent(team -> {
            TeamVote vote = votes.get(team);
            if (vote == null || over) {
                return;
            }

            vote.cast(voter.getUniqueId(), number);
            voter.sendMessage(Component.text("You voted for fortress " + number + ": ",
                            NamedTextColor.GREEN)
                    .append(Component.text(vote.candidate(number).name(), NamedTextColor.WHITE)));

            updateActionBars();   // the ticker closes the vote on its next pass if we are done
        });
    }

    /** What a player can vote for. Empty if they are not in this vote. */
    List<Candidate> optionsFor(Player player) {
        return teamOf(player)
                .map(votes::get)
                .map(TeamVote::candidates)
                .orElse(List.of());
    }

    /** What they picked, or null. */
    Integer voteOf(Player player) {
        return teamOf(player)
                .map(votes::get)
                .flatMap(vote -> vote.voteOf(player.getUniqueId()).stream().boxed().findFirst())
                .orElse(null);
    }

    private java.util.Optional<Integer> teamOf(Player player) {
        return context.teamOf(player).map(Team::index);
    }

    private boolean everyoneHasVoted() {
        for (Team team : context.teams()) {
            TeamVote vote = votes.get(team.index());
            if (vote == null) {
                continue;   // this team had nothing to choose from
            }
            long present = team.members().stream()
                    .map(Bukkit::getPlayer)
                    .filter(player -> player != null && player.isOnline())
                    .count();

            if (!vote.ready((int) present)) {
                return false;
            }
        }
        return true;
    }

    // --- what everybody sees ----------------------------------------------------------

    /**
     * The two things a voter wants to know: how many of <b>us</b> have voted, and whether
     * <b>they</b> are already waiting on us.
     */
    private void updateActionBars() {
        for (Team team : context.teams()) {
            TeamVote vote = votes.get(team.index());
            if (vote == null) {
                continue;
            }

            int others = 1 - team.index();
            TeamVote theirs = votes.get(others);

            String enemy = theirs == null ? "—" : (readiness(others) ? "READY" : "choosing…");

            Component bar = Component.text(secondsLeft + "s  ", NamedTextColor.YELLOW)
                    .append(Component.text("Your team: " + vote.votesCast() + "/"
                            + online(team) + " voted", NamedTextColor.GREEN))
                    .append(Component.text("   Enemy: " + enemy, NamedTextColor.GRAY))
                    .append(Component.text("   [Double-Shift to vote]", NamedTextColor.AQUA));

            context.onlinePlayers().stream()
                    .filter(player -> team.contains(player.getUniqueId()))
                    .forEach(player -> player.sendActionBar(bar));
        }
    }

    private boolean readiness(int team) {
        TeamVote vote = votes.get(team);
        return vote != null && vote.ready(online(context.teams().get(team)));
    }

    private int online(Team team) {
        return (int) team.members().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .count();
    }

    // --- the end ------------------------------------------------------------------------

    private void close(Runnable done) {
        over = true;
        if (ticker != null) {
            ticker.cancel();
        }
        registry.close(this);

        for (Team team : context.teams()) {
            TeamVote vote = votes.get(team.index());
            if (vote != null) {
                onDecided.accept(team.index(), vote.result());
            }
        }

        clearPlains();
        done.run();
    }

    /** Take the display fortresses down. They were scenery, and the match needs the room. */
    private void clearPlains() {
        int index = FortressMapBuilder.indexOf(context.arenaId());
        if (index < 0) {
            return;
        }

        int cube = config.fortressSize();
        int ox = index * FortressMapBuilder.SPACING;

        for (int team = 0; team < 2; team++) {
            for (int slot = 0; slot < 3; slot++) {
                int px = FortressMapBuilder.voteSlotX(ox, cube, slot);
                int pz = FortressMapBuilder.votePlainZ(0, team);

                Fortresses.clear(context.world(), px, FortressMapBuilder.VOTE_Y + 1, pz, cube);
            }
        }
    }

    /** Called if the match dies under us. */
    void abandon() {
        over = true;
        if (ticker != null) {
            ticker.cancel();
        }
        registry.close(this);
        clearPlains();
    }

    public boolean isOver() {
        return over;
    }

    UUID matchId() {
        return context.id();
    }
}

package fr.ayoub.pvp.core.match;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchOutcome;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.domain.br.BattleRoyaleRating;
import fr.ayoub.pvp.domain.rating.Division;
import fr.ayoub.pvp.domain.rating.DivisionLadder;
import fr.ayoub.pvp.domain.rating.EloCalculator;
import fr.ayoub.pvp.domain.rating.KFactor;
import fr.ayoub.pvp.domain.rating.Outcome;
import fr.ayoub.pvp.domain.rating.PlayerRating;
import fr.ayoub.pvp.domain.rating.RatingChange;
import fr.ayoub.pvp.domain.rating.RatingUpdater;
import fr.ayoub.pvp.domain.rating.TeamRating;
import fr.ayoub.pvp.storage.RatingRow;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the Elo change at the end of a ranked match.
 *
 * All the arithmetic lives in the (unit-tested) domain: {@link EloCalculator} decides
 * the new number, {@link RatingUpdater} decides what it does to the record. This class
 * only does the Bukkit and database part — off the main thread.
 */
public final class RatingService {

    private final PvPEnginePlugin plugin;
    private final EloCalculator elo = new EloCalculator(KFactor.standard(), 0);
    private final DivisionLadder ladder = DivisionLadder.standard();

    public RatingService(PvPEnginePlugin plugin) {
        this.plugin = plugin;
    }

    public DivisionLadder ladder() {
        return ladder;
    }

    /**
     * Move the ladder.
     *
     * A <b>draw is rated</b> — both sides score 0.5. An <b>abort is not</b>. Thirty minutes
     * of Fortress that ends level on kills is a result and belongs on the ladder; a server
     * restart is not, and rating it would hand people a rating change for a match nobody
     * finished. Collapsing the two was the old behaviour, and it quietly threw the draw away.
     */
    public void applyResult(Match match, MatchOutcome outcome) {
        if (!match.mode().ranked() || !outcome.isRated()) {
            return;
        }

        // A battle royale has no "other team" — it has a finishing order. Score it by placement.
        if (match.mode().ratingStyle() == GameModeDefinition.RatingStyle.PLACEMENT) {
            applyPlacement(match);
            return;
        }

        String modeId = match.mode().id();
        String format = match.format().id();
        List<Team> teams = match.teams();

        plugin.async().execute(() -> {
            // 1. Load every player's current record.
            Map<UUID, PlayerRating> before = new HashMap<>();
            for (Team team : teams) {
                for (UUID id : team.members()) {
                    before.put(id, plugin.ratings()
                            .find(id, modeId, format)
                            .map(RatingRow::toDomain)
                            .orElse(PlayerRating.initial(PvPEnginePlugin.STARTING_RATING)));
                }
            }

            // 2. A team is rated by its average — that is what the two sides compare.
            Map<Integer, Integer> teamAverage = new HashMap<>();
            for (Team team : teams) {
                List<Integer> ratings = team.members().stream()
                        .map(id -> before.get(id).rating())
                        .toList();
                teamAverage.put(team.index(), TeamRating.average(ratings));
            }

            // 3. New rating for each player, then persist.
            Map<UUID, RatingChange> changes = new HashMap<>();
            for (Team team : teams) {
                Outcome result = outcome.isDraw()
                        ? Outcome.DRAW
                        : team.index() == outcome.winningTeam() ? Outcome.WIN : Outcome.LOSS;
                int own = teamAverage.get(team.index());
                int opponents = averageOfOthers(teamAverage, team.index());

                for (UUID id : team.members()) {
                    PlayerRating current = before.get(id);

                    RatingChange change = elo.compute(current.toSnapshot(), own, opponents, result);
                    PlayerRating updated = RatingUpdater.apply(current, change.after(), result);

                    plugin.ratings().save(id, modeId, format, RatingRow.of(updated));
                    changes.put(id, change);
                }
            }

            // 4. Tell the players, back on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> announce(changes));
        });
    }

    /**
     * Rate a battle royale by finishing order.
     *
     * <p>Everyone's placement is read off the match (worked out from the elimination order) and
     * their kills from the scoreboard, and {@link BattleRoyaleRating} turns the whole field into
     * rating changes at once — reusing the same Elo maths as a duel, just resolved as every
     * pairwise comparison the placement implies. A solo team is one player, so each team maps to
     * one standing; a squad's members would share their team's placement and kills.
     */
    private void applyPlacement(Match match) {
        String modeId = match.mode().id();
        String format = match.format().id();
        int killBonus = match.mode().placementKillBonus();
        List<Team> teams = match.teams();

        plugin.async().execute(() -> {
            Map<UUID, PlayerRating> before = new HashMap<>();
            for (Team team : teams) {
                for (UUID id : team.members()) {
                    before.put(id, plugin.ratings()
                            .find(id, modeId, format)
                            .map(RatingRow::toDomain)
                            .orElse(PlayerRating.initial(PvPEnginePlugin.STARTING_RATING)));
                }
            }

            // One standing per player, in a stable order so the results line up with it.
            List<UUID> order = new ArrayList<>();
            List<BattleRoyaleRating.Standing> field = new ArrayList<>();
            for (Team team : teams) {
                for (UUID id : team.members()) {
                    order.add(id);
                    field.add(new BattleRoyaleRating.Standing(
                            before.get(id).toSnapshot(),
                            match.placement(team.index()),
                            match.kills(team.index())));
                }
            }

            List<RatingChange> results =
                    BattleRoyaleRating.compute(field, KFactor.standard(), killBonus);

            Map<UUID, RatingChange> changes = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                UUID id = order.get(i);
                RatingChange change = results.get(i);
                Outcome asResult = change.delta() >= 0 ? Outcome.WIN : Outcome.LOSS;   // for the record
                PlayerRating updated = RatingUpdater.apply(before.get(id), change.after(), asResult);

                plugin.ratings().save(id, modeId, format, RatingRow.of(updated));
                changes.put(id, change);
            }

            Bukkit.getScheduler().runTask(plugin, () -> announce(changes));
        });
    }

    /** With more than two teams, "the opponent" is the average of everyone else. */
    private int averageOfOthers(Map<Integer, Integer> teamAverage, int ownTeam) {
        List<Integer> others = new ArrayList<>();
        teamAverage.forEach((team, average) -> {
            if (team != ownTeam) {
                others.add(average);
            }
        });
        return TeamRating.average(others);
    }

    private void announce(Map<UUID, RatingChange> changes) {
        changes.forEach((id, change) -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                return;
            }

            int delta = change.delta();
            Division division = ladder.of(change.after());

            Component line = Component.text(delta >= 0 ? "+" + delta : String.valueOf(delta),
                            delta >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(" rating", NamedTextColor.GRAY))
                    .append(Component.text("  →  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(change.after(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + division.display() + ")", NamedTextColor.AQUA));

            player.sendMessage(line);
            player.sendActionBar(line);
        });
    }
}

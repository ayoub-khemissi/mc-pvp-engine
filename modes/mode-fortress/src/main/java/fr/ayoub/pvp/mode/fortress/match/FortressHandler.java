package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.MatchOutcome;
import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.domain.fortress.BlockIds;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.Candidate;
import fr.ayoub.pvp.domain.fortress.CubeRotation;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.map.FortressMapBuilder;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One Fortress match.
 *
 * Two fortresses, two crystals, thirty minutes. Break theirs and it is over; run the clock
 * out and the kills decide it; end level and it is a draw — which is a result, not a failure.
 *
 * The fortresses are fetched and pasted <b>before the countdown</b>, and the engine waits for
 * it: a fortress that materialises around a player who is already fighting is worse than no
 * fortress at all.
 */
public final class FortressHandler implements MatchHandler {

    private final Plugin plugin;
    private final FortressConfig config;
    private final FortressLibrary library;
    private final CrystalRegistry registry;
    private final VoteRegistry votes;

    private final Map<Integer, Crystal> crystals = new HashMap<>();
    private final Map<Integer, BossBar> bars = new HashMap<>();

    private MatchContext context;
    private BukkitTask hud;
    private VotePhase vote;

    public FortressHandler(Plugin plugin, FortressConfig config,
                           FortressLibrary library, CrystalRegistry registry,
                           VoteRegistry votes) {
        this.plugin = plugin;
        this.config = config;
        this.library = library;
        this.registry = registry;
        this.votes = votes;
    }

    @Override
    public void giveKit(MatchContext context, Player player, int team) {
        // Stone tools and food. Everything else is mined, looted, or taken off the body of
        // whoever you just killed.
        player.getInventory().addItem(
                new ItemStack(Material.STONE_PICKAXE),
                new ItemStack(Material.STONE_AXE),
                new ItemStack(Material.STONE_SHOVEL),
                new ItemStack(Material.COOKED_BEEF, 16));
    }

    // --- setting the match up ---------------------------------------------------------

    @Override
    public void onPrepare(MatchContext context, Runnable ready) {
        this.context = context;
        List<Team> teams = context.teams();

        // Off the main thread: this reads ratings and decodes up to six fortresses.
        PvPEngineApi.storage().async().execute(() -> {
            Map<Integer, List<Candidate>> candidates = new HashMap<>();
            Map<Integer, List<Blueprint>> previews = new HashMap<>();
            Map<UUID, Blueprint> byFortress = new HashMap<>();

            for (Team team : teams) {
                gather(context, team, candidates, previews, byFortress);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                resetMobs(context);

                vote = new VotePhase(plugin, config, context, votes,
                        (team, chosen) -> stand(context, team, byFortress.get(chosen.fortressId())));

                vote.start(candidates, previews, () -> {
                    bringEveryoneDown(context);
                    showBars(context);
                    ready.run();
                });
            });
        });
    }

    /**
     * What a team gets to choose from: <b>one fortress per member — their default</b>.
     *
     * Ordered by rating, best first, and that is not decoration. A tied vote — one-all in a
     * 2v2, which is not an edge case but the normal case — goes to candidate 1, and candidate
     * 1 is the best-rated player's fortress. Without the ordering the tie-break is a coin
     * toss.
     *
     * A team where nobody has anything playable gets nothing, and is told so: an empty pad
     * with no explanation reads as a broken server.
     */
    private void gather(MatchContext context, Team team,
                        Map<Integer, List<Candidate>> candidates,
                        Map<Integer, List<Blueprint>> previews,
                        Map<UUID, Blueprint> byFortress) {

        record Entry(int rating, Candidate candidate, Blueprint blueprint) {
        }

        List<Entry> entries = new ArrayList<>();

        for (UUID member : team.members()) {
            List<FortressLibrary.Checked> playable = library.playableFor(member);
            if (playable.isEmpty()) {
                continue;
            }

            // Their default if it is playable, otherwise the first one that is.
            FortressLibrary.Checked pick = playable.stream()
                    .filter(checked -> checked.fortress().isDefault())
                    .findFirst()
                    .orElse(playable.getFirst());

            library.forMatch(member, pick.slot()).ifPresent(blueprint -> {
                UUID id = UUID.randomUUID();   // this candidacy, not the row
                byFortress.put(id, blueprint);

                int rating = PvPEngineApi.lobby()
                        .ratingOf(member, context.mode(), context.format());

                entries.add(new Entry(rating, new Candidate(member, id, pick.name()), blueprint));
            });
        }

        if (entries.isEmpty()) {
            return;
        }

        entries.sort(Comparator.comparingInt(Entry::rating).reversed());

        candidates.put(team.index(), entries.stream().map(Entry::candidate).toList());
        previews.put(team.index(), entries.stream().map(Entry::blueprint).toList());
    }

    /** The vote is in. Stand this team's fortress on its pad. */
    private void stand(MatchContext context, int team, Blueprint blueprint) {
        if (blueprint == null) {
            context.broadcast(Component.text("Team " + (team + 1)
                    + " has no fortress: its pad is empty.", NamedTextColor.RED));
            return;
        }
        paste(context, team, blueprint);
    }

    /**
     * Undo the vote.
     *
     * The engine set everybody up on their spawn — kitted, frozen, in survival — and then we
     * took them a hundred blocks into the air and made them spectators. It is not going to do
     * that again: it hands the match to the countdown the moment we say we are ready. So
     * putting them back is ours to do, and forgetting it means a match that starts with two
     * ghosts floating over an island.
     */
    private void bringEveryoneDown(MatchContext context) {
        for (Team team : context.teams()) {
            for (Player player : context.onlinePlayers()) {
                if (!team.contains(player.getUniqueId())) {
                    continue;
                }

                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(context.spawn(team.index()));
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.setHealth(20);
                player.setFoodLevel(20);
                player.setFallDistance(0);

                giveKit(context, player, team.index());
                context.freeze(player, true);   // still nobody moves until FIGHT
            }
        }
    }

    /**
     * Stand a fortress on its pad.
     *
     * <b>Team 1's is turned half way round.</b> A blueprint's front is its z = 0 face and the
     * pads face each other down the map: pasted unrotated, the second team would defend a
     * fortress whose gate opens away from the enemy and whose blind wall takes the assault.
     * The blocks are turned as well as their positions — a staircase has to keep pointing the
     * way its builder pointed it.
     */
    private void paste(MatchContext context, int team, Blueprint blueprint) {
        Location pad = context.marker("fortress-pad-" + team).orElse(null);
        if (pad == null) {
            plugin.getLogger().severe("Map '" + context.arenaId() + "' has no marker "
                    + "'fortress-pad-" + team + "' — that pad will be empty.");
            return;
        }

        // Team 1 is turned half way round: the pads face each other, and a blueprint's front
        // is its z = 0 face. Unrotated, the second team would defend a fortress whose gate
        // opens away from the enemy and whose blind wall takes the assault.
        boolean turned = team == 1;

        Fortresses.paste(context, blueprint,
                pad.getBlockX(), pad.getBlockY(), pad.getBlockZ(), turned);

        if (blueprint.crystal() != null) {
            CubeRotation rotation = turned ? CubeRotation.HALF_TURN : CubeRotation.NONE;
            spawnCrystal(context, team, pad, rotation.apply(blueprint.crystal(), blueprint.size()));
        }
    }

    private void spawnCrystal(MatchContext context, int team, Location pad, BlockPos at) {
        Location location = new Location(context.world(),
                pad.getBlockX() + at.x() + 0.5,
                pad.getBlockY() + at.y(),
                pad.getBlockZ() + at.z() + 0.5);

        EnderCrystal entity = (EnderCrystal) context.world()
                .spawnEntity(location, EntityType.END_CRYSTAL);
        entity.setShowingBottom(false);

        Crystal crystal = new Crystal(team, entity, config.crystalHealth());
        crystals.put(team, crystal);
        registry.register(this, crystal);
    }

    /**
     * Put the map's creatures back.
     *
     * The engine's journal restores <b>blocks</b>. It cannot restore a zombie: nothing about
     * killing one changes a block, so nothing was written down, and the second match on this
     * island would find the cave already cleared and the hut already empty — the fights the
     * map was built around, already had.
     *
     * So the mobs are not part of the terrain. They are part of the <b>match</b>, and they are
     * set up like everything else in it: wipe whatever survived the last one, place the list
     * again.
     */
    private void resetMobs(MatchContext context) {
        int index = FortressMapBuilder.indexOf(context.arenaId());
        if (index < 0) {
            return;   // a designer's map: its mobs are its own business
        }

        // Only OUR island. Every instance shares one world, and another match is very
        // probably being fought two hundred blocks away — clearing "the monsters in the
        // world" would empty their cave in the middle of their game.
        int from = index * FortressMapBuilder.SPACING;
        int to = from + FortressMapBuilder.SIZE;

        for (Entity entity : context.world().getEntities()) {
            int x = entity.getLocation().getBlockX();
            if (entity instanceof Monster && x >= from && x < to) {
                entity.remove();
            }
        }

        for (FortressMapBuilder.Mob mob : FortressMapBuilder.mobs(context.world(), index)) {
            context.world().spawnEntity(mob.at(), mob.type());
        }
    }

    // --- the crystal being broken -------------------------------------------------------

    /**
     * A hit landed on a crystal. Called by the listener, which has already thrown the vanilla
     * damage away — an End Crystal has one hit point and explodes, and a fortress whose
     * crystal dies to the first arrow is not a fortress.
     */
    void onCrystalHit(Crystal crystal, int damage, Player attacker) {
        if (context == null || crystal.isDead()) {
            return;
        }

        boolean broken = crystal.damage(damage);
        updateBars();

        crystal.entity().getWorld().playSound(crystal.entity().getLocation(),
                broken ? Sound.ENTITY_ENDER_DRAGON_HURT : Sound.BLOCK_GLASS_BREAK, 2f, 1f);

        if (!broken) {
            return;
        }

        crystal.remove();
        registry.forget(crystal);

        int winner = 1 - crystal.team();   // whoever it did not belong to

        context.title(Component.text("CRYSTAL DOWN", NamedTextColor.RED),
                Component.text("Team " + (winner + 1) + " wins", NamedTextColor.WHITE));

        context.finish(MatchOutcome.win(winner, MatchOutcome.Reason.OBJECTIVE));
    }

    /** The crystal is only a target once the fight has actually started. */
    boolean isLive() {
        return context != null && context.secondsLeft() != 0;
    }

    // --- the clock ------------------------------------------------------------------------

    /**
     * Thirty minutes, and neither crystal fell.
     *
     * Most kills takes it. Level on kills is a <b>draw</b> — a real fight that ended dead even
     * is a result, and both sides score half on the ladder. Pretending somebody won it would
     * be a lie, and giving nobody anything would throw half an hour of play away.
     */
    @Override
    public MatchOutcome onTimeUp(MatchContext context) {
        int red = context.kills(0);
        int blue = context.kills(1);

        if (red == blue) {
            return MatchOutcome.draw();
        }
        return MatchOutcome.win(red > blue ? 0 : 1, MatchOutcome.Reason.TIME_LIMIT);
    }

    // --- the HUD ---------------------------------------------------------------------------

    private void showBars(MatchContext context) {
        for (Team team : context.teams()) {
            BossBar bar = BossBar.bossBar(Component.empty(), 1f,
                    team.index() == 0 ? BossBar.Color.RED : BossBar.Color.BLUE,
                    BossBar.Overlay.NOTCHED_10);

            bars.put(team.index(), bar);
            context.onlinePlayers().forEach(player -> player.showBossBar(bar));
        }

        updateBars();
        hud = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBars, 20L, 20L);
    }

    /** The two things a player needs to know at a glance: the crystals, and the clock. */
    private void updateBars() {
        if (context == null) {
            return;
        }

        for (Map.Entry<Integer, BossBar> entry : bars.entrySet()) {
            int team = entry.getKey();
            Crystal crystal = crystals.get(team);

            String health = crystal == null
                    ? "no crystal"
                    : crystal.health() + " / " + crystal.maxHealth();

            entry.getValue().name(Component.text("Team " + (team + 1), NamedTextColor.WHITE)
                    .append(Component.text("  ♦ " + health, NamedTextColor.AQUA))
                    .append(Component.text("   " + context.kills(team) + " kills",
                            NamedTextColor.GRAY))
                    .append(Component.text("   " + clock(), NamedTextColor.YELLOW)));

            entry.getValue().progress(crystal == null ? 0f : crystal.fraction());
        }
    }

    private String clock() {
        int left = context.secondsLeft();
        if (left < 0) {
            return "";
        }
        return String.format("%d:%02d", left / 60, left % 60);
    }

    // --- the end ----------------------------------------------------------------------------

    @Override
    public void onEnd(MatchContext context, MatchOutcome outcome) {
        if (hud != null) {
            hud.cancel();
            hud = null;
        }

        bars.values().forEach(bar -> context.onlinePlayers().forEach(player ->
                player.hideBossBar(bar)));
        bars.clear();

        new ArrayList<>(crystals.values()).forEach(crystal -> {
            registry.forget(crystal);
            crystal.remove();
        });
        crystals.clear();
    }
}

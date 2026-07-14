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
import fr.ayoub.pvp.domain.fortress.CrystalHitWindow;
import fr.ayoub.pvp.domain.fortress.CrystalRules;
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

    /** Three candidates. Not one per player — <b>three</b>. */
    private static final int CANDIDATES = 3;

    /**
     * What a team gets to choose from: <b>the three best fortresses the team can field</b>.
     *
     * It used to be one per member — their default — and that quietly killed the vote in 1v1:
     * a team of one produced a team of one candidate, so there was nothing to choose and the
     * default was simply taken. A player who has built three fortresses and is shown none of
     * them has not been given a vote, they have been given a fait accompli.
     *
     * So the slots are filled <b>round-robin</b>, best-rated player first: everybody's default
     * goes in, then everybody's second, until three are on the plain. In 3v3 that is one each.
     * In 1v1 it is all three of yours. In 2v2, one each and the better player's second — which
     * is the same rule, not a special case.
     *
     * The order is what settles a tied vote (see {@code TeamVote}): candidate 1 is the
     * best-rated player's first choice. Without it, a one-all split in a 2v2 — the normal
     * case, not an edge case — would be a coin toss.
     *
     * A team where nobody has anything playable gets nothing, and is told so: an empty pad
     * with no explanation reads as a broken server.
     */
    private void gather(MatchContext context, Team team,
                        Map<Integer, List<Candidate>> candidates,
                        Map<Integer, List<Blueprint>> previews,
                        Map<UUID, Blueprint> byFortress) {

        record Owner(UUID id, int rating, List<FortressLibrary.Checked> fortresses) {
        }

        List<Owner> owners = new ArrayList<>();

        for (UUID member : team.members()) {
            List<FortressLibrary.Checked> playable = new ArrayList<>(library.playableFor(member));
            if (playable.isEmpty()) {
                continue;
            }
            // Their default first: it is the one they meant.
            playable.sort(Comparator.comparing(
                    (FortressLibrary.Checked checked) -> checked.fortress().isDefault()).reversed());

            owners.add(new Owner(member, PvPEngineApi.lobby()
                    .ratingOf(member, context.mode(), context.format()), playable));
        }

        if (owners.isEmpty()) {
            return;
        }
        owners.sort(Comparator.comparingInt(Owner::rating).reversed());

        List<Candidate> chosen = new ArrayList<>();
        List<Blueprint> shown = new ArrayList<>();

        for (int round = 0; chosen.size() < CANDIDATES; round++) {
            boolean anyLeft = false;

            for (Owner owner : owners) {
                if (chosen.size() >= CANDIDATES || round >= owner.fortresses().size()) {
                    continue;
                }
                anyLeft = true;

                FortressLibrary.Checked pick = owner.fortresses().get(round);

                library.forMatch(owner.id(), pick.slot()).ifPresent(blueprint -> {
                    UUID candidacy = UUID.randomUUID();   // this offer, not the row
                    byFortress.put(candidacy, blueprint);

                    chosen.add(new Candidate(owner.id(), candidacy, pick.name()));
                    shown.add(blueprint);
                });
            }

            if (!anyLeft) {
                break;   // the team has fewer than three fortresses between them
            }
        }

        if (chosen.isEmpty()) {
            return;
        }

        candidates.put(team.index(), chosen);
        previews.put(team.index(), shown);
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

        // Which fortress gets turned round, and it is team 0's — not team 1's.
        //
        // A blueprint's front is its z = 0 face, and pasting puts that face at the pad's LOW
        // z. Team 0's pad is the one at low z, so its gate came out pointing AWAY from the
        // enemy and its blind wall took the assault. Team 1's pad is at high z, so it was
        // already right, and turning it made it wrong.
        boolean turned = team == 0;

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

        // Belt and braces: nothing else may be standing here. A crystal left behind by a match
        // that died badly would sit in the same block as ours, and a player would be hitting
        // one of two crystals with no way to tell which.
        for (Entity leftover : context.world().getNearbyEntities(location, 2, 2, 2)) {
            if (leftover instanceof EnderCrystal) {
                leftover.remove();
            }
        }

        EnderCrystal entity = (EnderCrystal) context.world()
                .spawnEntity(location, EntityType.END_CRYSTAL);
        entity.setShowingBottom(false);

        Crystal crystal = new Crystal(team, entity, location.getBlock().getRelative(0, -1, 0),
                config.crystalHealth(), config.crystalHitCooldownTicks());

        crystals.put(team, crystal);
        registry.register(this, crystal);
    }

    /** The block it stood on is gone. So is the crystal, and so is the match. */
    void onBaseBroken(Crystal crystal, Player attacker) {
        if (context == null || crystal.isDead()) {
            return;
        }
        crystal.kill();   // straight to zero: the obsidian under it is gone
        breakCrystal(crystal);
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
     * A blow landed on a crystal.
     *
     * <p>{@code raw} is what Minecraft says it was worth, and it already carries everything
     * that makes a hit good or bad: the weapon's damage, the attack cooldown (a fifth of it if
     * the swing was mashed, all of it if the player waited), ×1.5 for a critical, Sharpness,
     * Strength — and for an arrow, the draw and Power. We scale it, we never recompute it.
     *
     * <p>Then it goes through the attacker's own half-second window, and <b>that is the piece
     * that was missing</b>. The cooldown scaling alone does not punish spam: mashed at ten
     * clicks a second a diamond sword out-damages a patient one, 15.4 to 11.2. Vanilla only
     * escapes that because a mob is invulnerable for half a second after being hurt — and a
     * crystal is not a mob, so it never had that. See {@link CrystalHitWindow}.
     *
     * <p>Melee only. A bow rate-limits itself and a stick of TNT has to be carried there; the
     * one blow a player can spam is the one they swing.
     */
    void onCrystalHit(Crystal crystal, CrystalRules.Source source, double raw, Player attacker) {
        if (context == null || crystal.isDead()) {
            return;
        }

        // You cannot sabotage your own crystal. Not with a sword, not with an arrow, not with
        // a stick of TNT you dropped at its feet — a match should never be decided by one
        // player on the losing side deciding to end it.
        //
        // The obsidian under it is a different matter, and deliberately so: digging your own
        // fortress out is a thing players legitimately do, and drawing the line at "you may
        // not break this one block" would be a stranger rule than the own goal it prevents.
        if (attacker != null && isOwnCrystal(crystal, attacker)) {
            return;
        }

        double amount = config.crystalRules().damageOf(source, raw);
        if (amount <= 0) {
            return;
        }

        if (source == CrystalRules.Source.MELEE && attacker != null) {
            amount = crystal.admit(attacker.getUniqueId(), amount, Bukkit.getCurrentTick());
        }
        if (amount <= 0) {
            return;   // still inside their own window: the crystal does not react, as a mob would not
        }

        boolean broken = crystal.damage(amount);
        updateBars();

        crystal.entity().getWorld().playSound(crystal.entity().getLocation(),
                broken ? Sound.ENTITY_ENDER_DRAGON_HURT : Sound.BLOCK_GLASS_BREAK, 2f, 1f);

        if (broken) {
            breakCrystal(crystal);
        }
    }

    /** Is this player defending the crystal they are hitting? */
    private boolean isOwnCrystal(Crystal crystal, Player player) {
        return context.teamOf(player)
                .map(team -> team.index() == crystal.team())
                .orElse(false);
    }

    private void breakCrystal(Crystal crystal) {
        crystal.remove();
        registry.forget(crystal);
        updateBars();

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

    /**
     * What a Fortress bar is for: <b>the crystal</b>, and what it cost to get it there.
     *
     * <p>It used to end with the clock — on <em>both</em> bars, so the player was told the time
     * twice, side by side, in the two places their eye already was. The clock is not the mode's
     * to draw: the engine owns it, starts it, and ends the match on it, and it now shows it once
     * on the sidebar. A mode's HUD should carry what only the mode knows, and nothing else.
     */
    private void updateBars() {
        if (context == null) {
            return;
        }

        for (Map.Entry<Integer, BossBar> entry : bars.entrySet()) {
            int team = entry.getKey();
            Crystal crystal = crystals.get(team);

            String health = crystal == null
                    ? "destroyed"
                    : crystal.health() + " / " + crystal.maxHealth();

            entry.getValue().name(Component.text("TEAM " + (team + 1) + "  ", NamedTextColor.WHITE)
                    .append(Component.text("♦ " + health, NamedTextColor.AQUA))
                    .append(Component.text("   ⚔ " + context.kills(team),
                            NamedTextColor.GRAY)));

            entry.getValue().progress(crystal == null ? 0f : crystal.fraction());
        }
    }

    /**
     * They dropped out and came back. Their old connection took the boss bars with it — the
     * Player object those were shown to no longer exists — so they were playing the rest of the
     * match with no crystal health at all, and no way to know how close they were to losing.
     */
    @Override
    public void onRejoin(MatchContext context, Player player, int team) {
        bars.values().forEach(player::showBossBar);
        updateBars();
    }

    // --- the end ----------------------------------------------------------------------------

    @Override
    public void onEnd(MatchContext context, MatchOutcome outcome) {
        // The match can die UNDER the vote — an abort, a disconnect, a shutdown. If the vote
        // is left running it finishes anyway, thirty seconds later, and cheerfully pastes two
        // fortresses and two crystals into a match that no longer exists, on an arena that has
        // already been given to somebody else. That is where the duplicate crystals came from.
        if (vote != null && !vote.isOver()) {
            vote.abandon();
        }
        vote = null;

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

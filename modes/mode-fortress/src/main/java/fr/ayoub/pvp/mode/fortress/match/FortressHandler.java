package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.MatchOutcome;
import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.domain.fortress.BlockIds;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.CubeRotation;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
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

    private final Map<Integer, Crystal> crystals = new HashMap<>();
    private final Map<Integer, BossBar> bars = new HashMap<>();

    private MatchContext context;
    private BukkitTask hud;

    public FortressHandler(Plugin plugin, FortressConfig config,
                           FortressLibrary library, CrystalRegistry registry) {
        this.plugin = plugin;
        this.config = config;
        this.library = library;
        this.registry = registry;
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

        // Off the main thread: this reads and decodes two fortresses.
        PvPEngineApi.storage().async().execute(() -> {
            Map<Integer, Blueprint> chosen = new HashMap<>();
            for (Team team : teams) {
                pick(team).ifPresent(blueprint -> chosen.put(team.index(), blueprint));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Team team : teams) {
                    Blueprint blueprint = chosen.get(team.index());

                    if (blueprint == null) {
                        // Nobody in the team has a fortress today's rules accept. Say so —
                        // an empty pad with no explanation reads as a broken server.
                        context.broadcast(Component.text("Team " + (team.index() + 1)
                                + " has no fortress: its pad is empty.", NamedTextColor.RED));
                        continue;
                    }
                    paste(context, team.index(), blueprint);
                }

                showBars(context);
                ready.run();
            });
        });
    }

    /**
     * Which fortress the team plays.
     *
     * The vote comes next. For now: the first member's default, and failing that the first
     * playable fortress anybody in the team owns. Every path goes through the library, so
     * nothing today's rules reject can reach a pad — whatever the choosing rule becomes.
     */
    private Optional<Blueprint> pick(Team team) {
        for (UUID member : team.members()) {
            Optional<Blueprint> preferred = library.defaultForMatch(member);
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        for (UUID member : team.members()) {
            List<FortressLibrary.Checked> playable = library.playableFor(member);
            if (!playable.isEmpty()) {
                return library.forMatch(member, playable.getFirst().slot());
            }
        }
        return Optional.empty();
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

        CubeRotation rotation = team == 0 ? CubeRotation.NONE : CubeRotation.HALF_TURN;
        StructureRotation blocks = team == 0
                ? StructureRotation.NONE
                : StructureRotation.CLOCKWISE_180;

        int size = blueprint.size();

        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    BlockPos from = new BlockPos(x, y, z);
                    if (blueprint.isAir(from)) {
                        continue;
                    }
                    BlockPos to = rotation.apply(from, size);

                    place(context.world().getBlockAt(
                                    pad.getBlockX() + to.x(),
                                    pad.getBlockY() + to.y(),
                                    pad.getBlockZ() + to.z()),
                            blueprint.get(from), blocks);
                }
            }
        }

        if (blueprint.crystal() != null) {
            BlockPos at = rotation.apply(blueprint.crystal(), size);
            spawnCrystal(context, team, pad, at);
        }
    }

    private void place(Block block, String state, StructureRotation rotation) {
        try {
            var data = Bukkit.createBlockData(state);
            data.rotate(rotation);
            block.setBlockData(data, false);
            return;
        } catch (IllegalArgumentException e) {
            // The saved state did not survive a version change. A stair facing the wrong way
            // is a nuisance; a hole in a fortress is a lost match.
        }

        Material material = Material.matchMaterial(BlockIds.typeOf(state));
        if (material != null && material.isBlock()) {
            block.setType(material, false);
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

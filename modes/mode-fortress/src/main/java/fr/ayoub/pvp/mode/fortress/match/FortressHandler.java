package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.domain.fortress.BlockIds;
import fr.ayoub.pvp.domain.fortress.BlockPos;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.CubeRotation;
import fr.ayoub.pvp.mode.fortress.storage.FortressLibrary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One Fortress match: fetch each team's fortress, stand it on its pad, and light its crystal.
 *
 * The fortresses are loaded from the database <b>before the countdown</b>, and the engine
 * waits for it — a fortress that appears around a player who is already fighting is worse
 * than no fortress at all.
 */
public final class FortressHandler implements MatchHandler {

    private final Plugin plugin;
    private final FortressLibrary library;

    /** Where each team's crystal ended up. The thing the match is won on. */
    private final Map<Integer, EnderCrystal> crystals = new HashMap<>();

    public FortressHandler(Plugin plugin, FortressLibrary library) {
        this.plugin = plugin;
        this.library = library;
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

    @Override
    public void onPrepare(MatchContext context, Runnable ready) {
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
                        // Nobody in the team has a fortress today's rules will accept. The
                        // pad stays empty, and the team is told rather than left wondering.
                        context.broadcast(Component.text(
                                "Team " + (team.index() + 1) + " has no fortress — its pad is empty.",
                                NamedTextColor.RED));
                        continue;
                    }
                    paste(context, team.index(), blueprint);
                }
                ready.run();
            });
        });
    }

    /**
     * Which fortress the team plays.
     *
     * The vote comes next: for now, the first member's default, and failing that the first
     * playable fortress anybody in the team owns. Both go through
     * {@link FortressLibrary#forMatch} and its cousins, so nothing that today's rules reject
     * can reach a pad — whatever the choosing rule ends up being.
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
     * <b>Team 1's fortress is turned half way round.</b> A blueprint's front is its z = 0
     * face, and the two pads face each other down the map — pasted unrotated, the second team
     * would be defending a fortress whose gate opens away from the enemy and whose blind wall
     * faces the assault. The blocks are turned too, not just their positions: a staircase has
     * to keep pointing the way its builder pointed it.
     */
    private void paste(MatchContext context, int team, Blueprint blueprint) {
        Location pad = context.marker("fortress-pad-" + team).orElse(null);
        if (pad == null) {
            plugin.getLogger().severe("Map '" + context.arenaId()
                    + "' has no marker 'fortress-pad-" + team + "' — the pad will be empty.");
            return;
        }

        CubeRotation rotation = team == 0 ? CubeRotation.NONE : CubeRotation.HALF_TURN;
        StructureRotation blockRotation = team == 0
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
                    Block block = context.world().getBlockAt(
                            pad.getBlockX() + to.x(),
                            pad.getBlockY() + to.y(),
                            pad.getBlockZ() + to.z());

                    place(block, blueprint.get(from), blockRotation);
                }
            }
        }

        BlockPos crystal = blueprint.crystal();
        if (crystal != null) {
            BlockPos at = rotation.apply(crystal, size);
            crystals.put(team, spawnCrystal(context, pad, at));
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

    private EnderCrystal spawnCrystal(MatchContext context, Location pad, BlockPos at) {
        Location location = new Location(context.world(),
                pad.getBlockX() + at.x() + 0.5,
                pad.getBlockY() + at.y(),
                pad.getBlockZ() + at.z() + 0.5);

        EnderCrystal crystal = (EnderCrystal) context.world()
                .spawnEntity(location, EntityType.END_CRYSTAL);

        crystal.setShowingBottom(false);
        crystal.setInvulnerable(false);   // in a match it is very much a target
        return crystal;
    }

    /** The crystals of this match, for whatever reads them next (the HUD, the win check). */
    public Map<Integer, EnderCrystal> crystals() {
        return new HashMap<>(crystals);
    }

    @Override
    public void onEnd(MatchContext context, fr.ayoub.pvp.api.MatchOutcome outcome) {
        new ArrayList<>(crystals.values()).forEach(crystal -> {
            if (crystal.isValid()) {
                crystal.remove();
            }
        });
        crystals.clear();
    }
}

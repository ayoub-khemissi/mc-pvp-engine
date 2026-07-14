package fr.ayoub.pvp.mode.fortress.map;

import fr.ayoub.pvp.domain.fortress.IslandLayout;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Builds the Fortress map: an island you can dig, with a fortress pad at each end.
 *
 * A Fortress match is not a duel on a platform. It is thirty minutes of Minecraft: you mine,
 * you loot, you fight, and you take somebody's fortress apart. So the map has to actually
 * <b>contain</b> those things — stone and ore under your feet, trees to cut, water, a cave
 * with something unpleasant in it, and chests worth walking to.
 *
 * <p><b>Bedrock is the floor.</b> Everything above it is destructible, and that is the point;
 * bedrock is what stops a match ending because somebody dug a hole and fell out of the world.
 *
 * <p>Deterministic: the same instance is always built the same way. Two teams must not be
 * given different amounts of iron because a random number generator felt like it — and a
 * match that plays differently every time it is rebuilt cannot be debugged.
 *
 * <p>This is the development map. When a designer hands over a real one, it drops into
 * {@code plugins/PvPEngine/maps/} with the same {@code markers:} in its map.yml, and none of
 * this code runs again.
 */
public final class FortressMapBuilder {

    // --- the shape of an instance ---------------------------------------------------

    /**
     * The map's shape has changed. Bump this when it does, and the world is generated again.
     *
     * <p>Version 2 added the voting plains and had to raise the ceiling to hold them. Version 6
     * doubled the spacing and moved the plains out of sight of the match.
     *
     * <p>Version 7 changes <b>nothing</b>. It exists because version 6 shipped with a rebuild
     * that only deleted the map <em>files</em> and left every block of version 5 standing where
     * it was — so the new map was built beside the old one, and players fought on a fresh island
     * with the previous layout's voting plain hanging in the sky over them. Bumping again is what
     * makes a server that already took version 6 throw the world away and generate it clean.
     */
    public static final int MAP_VERSION = 8;

    /**
     * How far this map is rendered, in chunks. It is written into map.yml and the engine obeys
     * it (see Arena.Render): an island is 128 blocks across and a player standing at their own
     * gate has to SEE the enemy fortress, so the engine's small default would give them fog
     * where the target should be.
     *
     * <p>Everything below is spaced against it. Raise this and the spacings must grow with it —
     * which they will, because {@link IslandLayout} refuses to be built otherwise.
     */
    public static final int VIEW_DISTANCE = 10;
    public static final int SIMULATION_DISTANCE = 6;

    /** How far a player can actually see, in blocks. Chunks are sent whole; see IslandLayout. */
    private static final int SIGHT = IslandLayout.sightOf(VIEW_DISTANCE);

    public static final int SIZE = 128;          // the island, in blocks

    /**
     * Between two instances.
     *
     * <p>It was 256, which left 128 blocks of empty air between two islands — and a player sees
     * 192. Two matches running at once could watch each other, and nobody noticed because you
     * only ever test one at a time. The void in between costs nothing: its chunks hold no blocks
     * and are never loaded, because nobody is ever standing in them.
     */
    public static final int SPACING = 512;
    public static final int BEDROCK_Y = 40;
    public static final int SURFACE_Y = 62;      // the last solid block: you stand on 63
    public static final int CEILING_Y = 200;

    /** The pads sit near the two ends, facing each other along Z. */
    private static final int PAD_MARGIN = 18;

    /**
     * The geometry, checked against itself — <b>with the fortress size this server actually
     * configured</b>, because a bigger fortress makes the voting plain deeper and eats into the
     * gap behind it.
     *
     * <p>Called at boot. Every one of the three "X must not be visible from Y" rules in this
     * file has been broken at least once by somebody (me) changing an unrelated number, so they
     * are no longer comments — they are a constructor that throws.
     */
    public static IslandLayout layout(int cube) {
        return new IslandLayout(SIZE, SPACING, VOTE_Z_TEAM_0, VOTE_Z_TEAM_1,
                cube + VOTE_APRON + VOTE_GAP, SIGHT);
    }

    /**
     * The voting plains: one per team, and far enough from everything to be invisible from it.
     *
     * <p>They were 36 blocks apart, and a team could stand on its own plain and read the enemy's
     * three fortresses like a menu. That was moved to 300 — comfortably past the view distance
     * <b>of the day</b>, which was 6 chunks. Then the view distance became 10, and 300 became
     * close enough that a player at the far edge of the island had the plain hanging in the sky
     * over their match.
     *
     * <p>A wall would not have helped either time: a spectator flies through walls. Distance is
     * the only mechanism that works, and it is free — so these are now spaced against SIGHT, and
     * {@link IslandLayout} throws at boot if any of it ever stops being true.
     */
    public static final int VOTE_Y = 130;
    private static final int VOTE_Z_TEAM_0 = 512;
    private static final int VOTE_Z_TEAM_1 = 1024;
    private static final int VOTE_GAP = 8;       // between two fortresses on display

    /**
     * How far back the voters stand.
     *
     * It was ten, which put a player's face against the middle sign with three fortresses
     * looming over them — you cannot choose between three buildings you are standing inside.
     * Forty is far enough back to see all three at once, which is the entire job of this
     * place.
     */
    private static final int VOTE_APRON = 40;

    private final Plugin plugin;
    private final FortressConfig config;

    public FortressMapBuilder(Plugin plugin, FortressConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public int build(World world, int instances) {
        return build(world, 0, instances);
    }

    /**
     * Build instances {@code [from, to)}, and leave the others alone.
     *
     * The range matters: raising {@code map.instances} must add islands without touching the
     * ones that are already there. Rebuilding an island somebody is fighting on would drop a
     * fresh layer of stone through their fortress in the middle of their match.
     */
    public int build(World world, int from, int to) {
        int blocks = 0;

        for (int index = from; index < to; index++) {
            blocks += buildInstance(world, index);
            writeMapFile(world, index);
        }
        return blocks;
    }

    private int buildInstance(World world, int index) {
        int ox = index * SPACING;
        int oz = 0;

        // Deterministic per instance: same map, every time, for everyone.
        Random random = new Random(0xF0F0 + index);

        int blocks = 0;
        blocks += ground(world, ox, oz, random);
        blocks += pads(world, ox, oz);
        blocks += lake(world, ox, oz);
        blocks += trees(world, ox, oz, random);
        blocks += cave(world, ox, oz);
        blocks += house(world, ox, oz);
        blocks += votingPlains(world, ox, oz);
        blocks += walls(world, ox, oz);

        return blocks;
    }

    // --- the voting plains ------------------------------------------------------------

    /**
     * Where a team stands and picks the fortress it is going to fight behind.
     *
     * One per team, far apart and high above the island — you look at your own three, not at
     * the enemy's. The candidates are pasted onto it when the match starts and taken down
     * again when the vote ends; all that is built here is the floor they stand on and the
     * numbers in front of them.
     */
    private int votingPlains(World world, int ox, int oz) {
        int cube = config.fortressSize();
        int width = 3 * cube + 2 * VOTE_GAP;
        int depth = cube + VOTE_APRON;
        int blocks = 0;

        for (int team = 0; team < 2; team++) {
            int px = votePlainX(ox);
            int pz = votePlainZ(oz, team);

            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    world.getBlockAt(px + x, VOTE_Y, pz + z)
                            .setType(Material.SMOOTH_STONE, false);
                    blocks++;
                }
            }

            // 1, 2, 3 — on the floor AT THE FOOT OF EACH FORTRESS, not at the voter's feet.
            // The number belongs to the building, not to the place you happen to be standing.
            for (int slot = 0; slot < 3; slot++) {
                int sx = px + slot * (cube + VOTE_GAP) + cube / 2;
                int sz = pz + cube + 2;

                world.getBlockAt(sx, VOTE_Y, sz).setType(Material.GOLD_BLOCK, false);
                Block sign = world.getBlockAt(sx, VOTE_Y + 1, sz);
                sign.setType(Material.OAK_SIGN, false);

                if (sign.getState() instanceof org.bukkit.block.Sign text) {
                    text.getSide(org.bukkit.block.sign.Side.FRONT)
                            .line(1, net.kyori.adventure.text.Component.text("[ " + (slot + 1) + " ]",
                                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    text.setWaxed(true);
                    text.update();
                }
                blocks += 2;
            }
        }
        return blocks;
    }

    /** The corner of a voting plain: fortress 1 is pasted here, 2 and 3 to its right. */
    public static int votePlainX(int ox) {
        return ox + 8;
    }

    public static int votePlainZ(int oz, int team) {
        return team == 0 ? oz + VOTE_Z_TEAM_0 : oz + VOTE_Z_TEAM_1;
    }

    /** Where fortress {@code slot} (0-based) is shown. */
    public static int voteSlotX(int ox, int cube, int slot) {
        return votePlainX(ox) + slot * (cube + VOTE_GAP);
    }

    /** Where the team stands: forty blocks back, centred, looking at all three at once. */
    public static Location voteSpawn(World world, int index, int cube, int team) {
        int ox = index * SPACING;
        int oz = 0;

        double x = votePlainX(ox) + (3 * cube + 2 * VOTE_GAP) / 2.0;
        double z = votePlainZ(oz, team) + cube + VOTE_APRON - 2.5;

        // A little above the floor: the three of them are twenty blocks tall, and you want to
        // see the roofs, not three walls.
        Location at = new Location(world, x, VOTE_Y + 4, z);
        at.setYaw(180f);    // looking back down the plain, at the fortresses
        at.setPitch(5f);
        return at;
    }

    // --- the ground -----------------------------------------------------------------

    /** Bedrock, then stone with ore in it, then dirt, then grass. */
    private int ground(World world, int ox, int oz, Random random) {
        int blocks = 0;

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                world.getBlockAt(ox + x, BEDROCK_Y, oz + z).setType(Material.BEDROCK, false);
                blocks++;

                for (int y = BEDROCK_Y + 1; y <= SURFACE_Y - 4; y++) {
                    world.getBlockAt(ox + x, y, oz + z).setType(Material.STONE, false);
                    blocks++;
                }
                for (int y = SURFACE_Y - 3; y < SURFACE_Y; y++) {
                    world.getBlockAt(ox + x, y, oz + z).setType(Material.DIRT, false);
                    blocks++;
                }
                world.getBlockAt(ox + x, SURFACE_Y, oz + z).setType(Material.GRASS_BLOCK, false);
                blocks++;
            }
        }

        blocks += ore(world, ox, oz, random, Material.COAL_ORE, 40, 8);
        blocks += ore(world, ox, oz, random, Material.IRON_ORE, 28, 6);
        blocks += ore(world, ox, oz, random, Material.DIAMOND_ORE, 6, 3);

        return blocks;
    }

    /** Veins, not sprinkles: a player who finds iron should get enough of it to matter. */
    private int ore(World world, int ox, int oz, Random random, Material ore, int veins, int size) {
        int blocks = 0;

        for (int vein = 0; vein < veins; vein++) {
            int x = 4 + random.nextInt(SIZE - 8);
            int z = 4 + random.nextInt(SIZE - 8);
            int y = BEDROCK_Y + 2 + random.nextInt(SURFACE_Y - 8 - BEDROCK_Y);

            for (int i = 0; i < size; i++) {
                int bx = ox + x + random.nextInt(3) - 1;
                int bz = oz + z + random.nextInt(3) - 1;
                int by = y + random.nextInt(3) - 1;

                Block block = world.getBlockAt(bx, by, bz);
                if (block.getType() == Material.STONE) {
                    block.setType(ore, false);
                    blocks++;
                }
            }
        }
        return blocks;
    }

    // --- the two fortress pads -------------------------------------------------------

    /**
     * A flat square of stone for each fortress, and the ground under it dug out — a fortress
     * that half-buries itself in a hillock is a fortress with a gate that opens into dirt.
     */
    private int pads(World world, int ox, int oz) {
        int cube = config.fortressSize();
        int blocks = 0;

        for (int team = 0; team < 2; team++) {
            int px = padX(ox, cube);
            int pz = padZ(oz, cube, team);

            for (int x = 0; x < cube; x++) {
                for (int z = 0; z < cube; z++) {
                    // The pad itself: the block a fortress stands ON.
                    world.getBlockAt(px + x, SURFACE_Y, pz + z)
                            .setType(Material.STONE_BRICKS, false);
                    blocks++;

                    // And nothing above it, so the fortress lands in clear air.
                    for (int y = SURFACE_Y + 1; y < SURFACE_Y + 1 + cube; y++) {
                        world.getBlockAt(px + x, y, pz + z).setType(Material.AIR, false);
                    }
                }
            }
        }
        return blocks;
    }

    /** Centred on X. */
    public static int padX(int ox, int cube) {
        return ox + (SIZE - cube) / 2;
    }

    /** Team 0 near z = 0, team 1 near z = SIZE. They face each other. */
    public static int padZ(int oz, int cube, int team) {
        return team == 0 ? oz + PAD_MARGIN : oz + SIZE - PAD_MARGIN - cube;
    }

    // --- things worth walking to -----------------------------------------------------

    private int lake(World world, int ox, int oz) {
        int cx = ox + SIZE / 2;
        int cz = oz + SIZE / 2;
        int radius = 12;
        int blocks = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                world.getBlockAt(cx + x, SURFACE_Y, cz + z).setType(Material.WATER, false);
                world.getBlockAt(cx + x, SURFACE_Y - 1, cz + z).setType(Material.WATER, false);
                world.getBlockAt(cx + x, SURFACE_Y - 2, cz + z).setType(Material.SAND, false);
                blocks += 3;
            }
        }
        return blocks;
    }

    private int trees(World world, int ox, int oz, Random random) {
        int blocks = 0;

        for (int i = 0; i < 40; i++) {
            int x = ox + 6 + random.nextInt(SIZE - 12);
            int z = oz + 6 + random.nextInt(SIZE - 12);

            if (world.getBlockAt(x, SURFACE_Y, z).getType() != Material.GRASS_BLOCK) {
                continue;   // not on the lake, not on a pad
            }

            int height = 4 + random.nextInt(3);
            for (int y = 1; y <= height; y++) {
                world.getBlockAt(x, SURFACE_Y + y, z).setType(Material.OAK_LOG, false);
                blocks++;
            }
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    for (int ly = height - 1; ly <= height + 1; ly++) {
                        if (Math.abs(lx) + Math.abs(lz) + Math.abs(ly - height) > 3) {
                            continue;
                        }
                        Block leaf = world.getBlockAt(x + lx, SURFACE_Y + ly, z + lz);
                        if (leaf.getType().isAir()) {
                            leaf.setType(Material.OAK_LEAVES, false);
                            blocks++;
                        }
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * A cave, dug into the stone, with spiders and skeletons in it and a chest at the back.
     *
     * Deliberate, not natural: the loot is worth the fight, and the fight is where the map
     * decides you have to go.
     */
    private int cave(World world, int ox, int oz) {
        int cx = ox + 24;
        int cz = oz + SIZE / 2;
        int floor = BEDROCK_Y + 6;
        int blocks = 0;

        // The chamber.
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = 0; y <= 5; y++) {
                    if (x * x + z * z > 49) {
                        continue;
                    }
                    world.getBlockAt(cx + x, floor + y, cz + z).setType(Material.AIR, false);
                    blocks++;
                }
            }
        }

        // The way down into it, from the surface.
        for (int y = floor; y <= SURFACE_Y; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    world.getBlockAt(cx + 5 + x, y, cz + z).setType(Material.AIR, false);
                }
            }
            world.getBlockAt(cx + 5, y, cz).setType(Material.LADDER, false);
        }

        torch(world, cx - 4, floor + 2, cz);
        torch(world, cx + 4, floor + 2, cz);

        chest(world, cx - 5, floor, cz, List.of(
                new ItemStack(Material.IRON_INGOT, 8),
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.GOLDEN_APPLE, 1),
                new ItemStack(Material.BREAD, 8)));

        return blocks;
    }

    /** A hut with a zombie in it and something useful on the floor. */
    private int house(World world, int ox, int oz) {
        int hx = ox + SIZE - 30;
        int hz = oz + SIZE / 2;
        int blocks = 0;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
                    boolean roof = y == 4;
                    boolean door = z == 0 && x == 3 && y <= 2;

                    Block block = world.getBlockAt(hx + x, SURFACE_Y + y, hz + z);

                    if (door) {
                        block.setType(Material.AIR, false);
                    } else if (roof) {
                        block.setType(Material.OAK_PLANKS, false);
                        blocks++;
                    } else if (wall) {
                        block.setType(Material.COBBLESTONE, false);
                        blocks++;
                    }
                }
            }
        }

        torch(world, hx + 3, SURFACE_Y + 3, hz + 5);

        chest(world, hx + 5, SURFACE_Y + 1, hz + 5, List.of(
                new ItemStack(Material.LEATHER_CHESTPLATE),
                new ItemStack(Material.COOKED_BEEF, 6),
                new ItemStack(Material.OAK_PLANKS, 32)));

        return blocks;
    }

    // --- the mobs -----------------------------------------------------------------------

    /** A creature the map put somewhere on purpose. */
    public record Mob(Location at, EntityType type) {
    }

    /**
     * Where the map wants the danger to be — as <b>data</b>, not as a side effect of building.
     *
     * A mob killed in a match does not come back, and blocks are the only thing the engine's
     * journal can put back: it restores the world, not the things walking around in it. So
     * the mobs cannot be placed once, while the island is built. They have to be a list the
     * <b>match</b> can ask for and set up again, every time.
     */
    public static List<Mob> mobs(World world, int index) {
        int ox = index * SPACING;
        int oz = 0;

        int cx = ox + 24;
        int cz = oz + SIZE / 2;
        int floor = BEDROCK_Y + 6;

        int hx = ox + SIZE - 30;
        int hz = oz + SIZE / 2;

        return List.of(
                new Mob(new Location(world, cx + 0.5, floor + 1, cz + 3.5), EntityType.SPIDER),
                new Mob(new Location(world, cx - 2.5, floor + 1, cz - 1.5), EntityType.SPIDER),
                new Mob(new Location(world, cx + 2.5, floor + 1, cz - 3.5), EntityType.SKELETON),
                new Mob(new Location(world, hx + 3.5, SURFACE_Y + 1, hz + 3.5), EntityType.ZOMBIE));
    }

    /** "fortress-3" → 2. The instance an arena is, which is what its layout is keyed on. */
    public static int indexOf(String arenaId) {
        try {
            return Integer.parseInt(arenaId.substring(arenaId.lastIndexOf('-') + 1)) - 1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // --- the wall around it -----------------------------------------------------------

    /**
     * A barrier shell around the island.
     *
     * Client-side, so a player who runs at the edge simply stops, rather than being dragged
     * back by the server a moment later. The engine's own region check is behind it, wider,
     * as the net for anything that ignores blocks — an ender pearl, a cheat.
     */
    private int walls(World world, int ox, int oz) {
        int blocks = 0;
        int top = SURFACE_Y + 40;

        for (int i = -1; i <= SIZE; i++) {
            for (int y = BEDROCK_Y; y <= top; y++) {
                world.getBlockAt(ox + i, y, oz - 1).setType(Material.BARRIER, false);
                world.getBlockAt(ox + i, y, oz + SIZE).setType(Material.BARRIER, false);
                world.getBlockAt(ox - 1, y, oz + i).setType(Material.BARRIER, false);
                world.getBlockAt(ox + SIZE, y, oz + i).setType(Material.BARRIER, false);
                blocks += 4;
            }
        }
        return blocks;
    }

    // --- the map file -----------------------------------------------------------------

    /**
     * Write the map.yml the engine reads — including the {@code markers} that tell Fortress
     * where its pads are.
     *
     * The engine has no idea what "fortress-pad-0" means. It carries the point and hands it
     * back when asked, which is why a new mode never needs a new engine.
     */
    private static java.util.Map<String, Object> box(int minX, int minY, int minZ,
                                                     int maxX, int maxY, int maxZ) {
        java.util.Map<String, Object> box = new java.util.LinkedHashMap<>();
        box.put("min-x", minX);
        box.put("min-y", minY);
        box.put("min-z", minZ);
        box.put("max-x", maxX);
        box.put("max-y", maxY);
        box.put("max-z", maxZ);
        return box;
    }

    private void writeMapFile(World world, int index) {
        int cube = config.fortressSize();
        int ox = index * SPACING;
        int oz = 0;

        YamlConfiguration yaml = new YamlConfiguration();
        String id = "fortress-" + (index + 1);

        yaml.set("id", id);
        yaml.set("world", world.getName());
        yaml.set("modes", List.of("fortress"));
        yaml.set("fortress-map-version", MAP_VERSION);

        // How far this map has to be RENDERED. The engine defaults to something small,
        // because a duel arena is 48 blocks wide and rendering the void around it is waste
        // multiplied by the number of concurrent matches. An island is 128 blocks across,
        // and a player standing at their own gate must be able to SEE the enemy fortress —
        // at the engine's default they would be staring at fog where the target should be.
        //
        // 8 chunks covers the island, +2 so the far wall is not on the edge of the fog.
        yaml.set("view-distance", VIEW_DISTANCE);
        // Redstone traps only need to tick when somebody is close enough to trip them.
        yaml.set("simulation-distance", SIMULATION_DISTANCE);

        for (int team = 0; team < 2; team++) {
            int px = padX(ox, cube);
            int pz = padZ(oz, cube, team);

            // AT YOUR GATE, not behind the fortress.
            //
            // You spawn on the face the enemy arrives at — your own front — with your fortress
            // at your back and the map in front of you. Spawning behind it meant staring at a
            // blind wall while the fight happened on the other side of it.
            double sx = px + cube / 2.0;
            double sz = team == 0 ? pz + cube + 6.5 : pz - 6.5;

            yaml.set("spawns.team-" + team + ".x", sx);
            yaml.set("spawns.team-" + team + ".y", (double) SURFACE_Y + 1);
            yaml.set("spawns.team-" + team + ".z", sz);
            yaml.set("spawns.team-" + team + ".yaw", team == 0 ? 0.0 : 180.0);
            yaml.set("spawns.team-" + team + ".pitch", 0.0);

            // The pad's corner: the block a fortress's (0, 0, 0) lands on top of.
            yaml.set("markers.fortress-pad-" + team + ".x", (double) px);
            yaml.set("markers.fortress-pad-" + team + ".y", (double) SURFACE_Y + 1);
            yaml.set("markers.fortress-pad-" + team + ".z", (double) pz);
        }

        yaml.set("bounds.type", "cuboid");
        yaml.set("bounds.min-x", (double) ox - 1);
        yaml.set("bounds.min-y", (double) BEDROCK_Y);
        yaml.set("bounds.min-z", (double) oz - 1);
        yaml.set("bounds.max-x", (double) ox + SIZE);
        // High enough to hold the voting plains. The engine pushes players back inside the
        // bounds, so a ceiling below them would drag a voter out of the vote.
        yaml.set("bounds.max-y", (double) CEILING_Y);
        // Far enough to hold the voting plains. The engine pushes a player back inside the
        // bounds, so a boundary that stopped at the island would drag a voter out of the vote.
        // Nothing is out there but the two plains: the island itself is walled with barriers.
        yaml.set("bounds.max-z", (double) oz + VOTE_Z_TEAM_1 + 150);

        // WHAT THE ENGINE PUTS BACK BETWEEN MATCHES.
        //
        // Not the bounds — those reach a thousand blocks down Z to take in the voting plains, and
        // photographing eight million blocks of empty sky would be absurd. Only what can actually
        // be CHANGED: the island, which is dug and blown up and built on, and the two plains,
        // which have three fortresses pasted onto them and taken off again.
        //
        // The engine never learns what any of it is. It photographs these boxes once, on a map
        // that is whole, and puts them back after every match. A designer's map will say the same
        // thing in the same place, and the engine will not know the difference.
        int plainWidth = 3 * cube + 2 * VOTE_GAP;
        int plainDepth = cube + VOTE_APRON + VOTE_GAP;

        List<java.util.Map<String, Object>> reset = new java.util.ArrayList<>();
        reset.add(box(ox, BEDROCK_Y, oz, ox + SIZE - 1, SURFACE_Y + 40, oz + SIZE - 1));

        for (int team = 0; team < 2; team++) {
            int px = votePlainX(ox);
            int pz = votePlainZ(oz, team);
            reset.add(box(px - 2, VOTE_Y, pz - 2,
                    px + plainWidth + 2, VOTE_Y + cube + 4, pz + plainDepth + 2));
        }
        yaml.set("reset", reset);

        File maps = new File(plugin.getDataFolder().getParentFile(), "PvPEngine/maps");
        maps.mkdirs();

        try {
            yaml.save(new File(maps, id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write the map file for " + id + ": " + e.getMessage());
        }
    }

    // --- odds and ends -----------------------------------------------------------------

    private void torch(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.TORCH, false);
    }

    private void chest(World world, int x, int y, int z, List<ItemStack> loot) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);

        if (block.getState() instanceof Chest chest) {
            loot.forEach(item -> chest.getInventory().addItem(item));
            chest.update();
        }
    }

    /** A mob, placed on purpose. The map decides where the danger is, not the spawn rules. */
    private void spawn(World world, int x, int y, int z, EntityType type) {
        world.spawnEntity(new Location(world, x + 0.5, y, z + 0.5), type);
    }
}

package fr.ayoub.pvp.mode.fortress;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchContext;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.MatchRules;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.menu.FortressMenu;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Fortress: two teams, two fortresses, two End Crystals. Break theirs, or out-kill them
 * in thirty minutes.
 *
 * The match itself is not wired yet — this milestone is the build zone. What is real today:
 * the mode is in the compass at rank 2, it queues, and it takes you to a place where you
 * can build the thing you will later fight over.
 */
public final class FortressMode implements GameModeDefinition {

    private final FortressConfig config;
    private final BuildZoneService zones;
    private final FortressRepository fortresses;

    public FortressMode(FortressConfig config, BuildZoneService zones, FortressRepository fortresses) {
        this.config = config;
        this.zones = zones;
        this.fortresses = fortresses;
    }

    @Override
    public String id() {
        return "fortress";
    }

    @Override
    public Component displayName() {
        return Component.text("Fortress", NamedTextColor.DARK_PURPLE);
    }

    /** Second in the menu, behind Duel — and first the day Duel is switched off. */
    @Override
    public int order() {
        return 2;
    }

    @Override
    public ItemStack icon() {
        ItemStack icon = new ItemStack(Material.BRICKS);
        icon.editMeta(meta -> {
            meta.displayName(displayName().decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Build a fortress. Defend your crystal.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Break theirs.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return icon;
    }

    @Override
    public List<Format> formats() {
        return List.of(Format.parse("1v1"), Format.parse("2v2"), Format.parse("3v3"));
    }

    @Override
    public boolean ranked() {
        return true;
    }

    /**
     * One long round, and you may build: a fortress match is a thirty-minute survival game
     * on a destructible map, not a duel. {@code building = true} is what tells the engine to
     * put players in SURVIVAL and stop cancelling their blocks.
     */
    @Override
    public MatchRules rules() {
        return new MatchRules(1, 5, 30 * 60, true);
    }

    /** Fortress brings its own screen: it queues, and it sends you to the build zone. */
    @Override
    public ModeScreen screen() {
        return player -> new FortressMenu(this, config, zones, fortresses).open(player);
    }

    @Override
    public MatchHandler createHandler() {
        return new MatchHandler() {
            @Override
            public void giveKit(MatchContext context, Player player, int team) {
                // The starter kit: stone tools and some food. Everything else is mined,
                // looted, or taken off the body of whoever you just killed.
                player.getInventory().addItem(
                        new ItemStack(Material.STONE_PICKAXE),
                        new ItemStack(Material.STONE_AXE),
                        new ItemStack(Material.STONE_SHOVEL),
                        new ItemStack(Material.COOKED_BEEF, 16));
            }
        };
    }
}

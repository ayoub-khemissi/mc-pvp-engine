package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.PvPEngineApi;
import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.match.Format;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.mode.fortress.FortressConfig;
import fr.ayoub.pvp.mode.fortress.build.BuildZoneService;
import fr.ayoub.pvp.mode.fortress.storage.FortressRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The screen the compass opens for Fortress: queue, or go and build.
 *
 * This is the whole reason the SPI grew a {@code screen()} hook. The engine's default screen
 * lists formats and nothing else — which is all Duel will ever need. Fortress has somewhere
 * else to send you, and the engine still has no idea that place exists.
 */
public final class FortressMenu extends Menu {

    private static final int SLOT_BUILD = 31;

    private final GameModeDefinition mode;
    private final FortressConfig config;
    private final BuildZoneService zones;
    private final FortressRepository fortresses;

    public FortressMenu(GameModeDefinition mode, FortressConfig config,
                        BuildZoneService zones, FortressRepository fortresses) {
        super(Component.text("Fortress", NamedTextColor.DARK_PURPLE), MenuLayout.bordered(4));
        this.mode = mode;
        this.config = config;
        this.zones = zones;
        this.fortresses = fortresses;
    }

    @Override
    protected void build(Player viewer) {
        List<Format> formats = mode.formats();

        for (int i = 0; i < formats.size() && i < layout().itemsPerPage(); i++) {
            Format format = formats.get(i);

            set(layout().slotAt(i), Icons.of(Material.IRON_SWORD,
                            Component.text(format.id(), NamedTextColor.GREEN),
                            Component.text(format.totalPlayers() + " players", NamedTextColor.GRAY),
                            Component.text("Ranked", NamedTextColor.DARK_GRAY),
                            Component.empty(),
                            Component.text("Your team votes for the fortress it plays.",
                                    NamedTextColor.DARK_GRAY)),
                    event -> PvPEngineApi.lobby().queue(viewer, mode, format));
        }

        set(SLOT_BUILD, Icons.of(Material.BRICKS,
                        Component.text("My fortresses", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Build, edit and choose your default.", NamedTextColor.GRAY),
                        Component.text(config.slots() + " slots", NamedTextColor.DARK_GRAY)),
                event -> new SlotMenu(config, zones, fortresses, this).open(viewer));
    }
}

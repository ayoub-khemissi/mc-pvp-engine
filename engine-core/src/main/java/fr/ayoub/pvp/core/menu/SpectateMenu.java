package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.api.Team;
import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.match.Match;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** The live matches you can watch. Click one, you are in it — as a ghost. */
public final class SpectateMenu extends Menu {

    private final PvPEnginePlugin plugin;

    public SpectateMenu(PvPEnginePlugin plugin) {
        super(Component.text("Spectate", NamedTextColor.DARK_AQUA), MenuLayout.bordered(6));
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        List<Match> matches = plugin.matches().active().stream()
                .filter(match -> !match.state().isFinished())
                .toList();

        if (matches.isEmpty()) {
            set(layout().slotAt(4), Icons.of(Material.BARRIER,
                    Component.text("No match is running", NamedTextColor.RED),
                    Component.text("Come back when someone is fighting.", NamedTextColor.GRAY)));
            return;
        }

        List<Match> shown = pageItems(matches);

        for (int i = 0; i < shown.size(); i++) {
            Match match = shown.get(i);

            List<Component> lore = new ArrayList<>();
            for (Team team : match.teams()) {
                lore.add(Component.text("Team " + (team.index() + 1) + ": ", NamedTextColor.GRAY)
                        .append(Component.text(names(team.members()), NamedTextColor.WHITE)));
            }
            lore.add(Component.text("Round " + match.round() + " of " + match.bestOf()
                    + "  —  " + match.series().scoreline(), NamedTextColor.YELLOW));
            lore.add(Component.text("Click to watch", NamedTextColor.GREEN));

            set(layout().slotAt(i), Icons.of(Material.ENDER_EYE,
                            Component.text(match.mode().id() + " " + match.format().id(),
                                    NamedTextColor.AQUA),
                            lore.toArray(Component[]::new)),
                    event -> {
                        viewer.closeInventory();
                        plugin.matches().spectate(viewer, match);
                    });
        }

        paginate(viewer, matches.size());
    }

    private static String names(List<UUID> members) {
        return members.stream()
                .map(id -> {
                    Player player = Bukkit.getPlayer(id);
                    return player != null ? player.getName() : "?";
                })
                .collect(Collectors.joining(", "));
    }
}

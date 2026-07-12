package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** Who you can invite: everyone in the lobby who is free. Click a head, done. */
public final class InviteMenu extends Menu {

    private static final int SLOT_BACK = 49;

    private final PvPEnginePlugin plugin;
    private int page;

    public InviteMenu(PvPEnginePlugin plugin) {
        super(Component.text("Invite a player", NamedTextColor.DARK_GREEN), MenuLayout.bordered(6));
        this.plugin = plugin;
    }

    @Override
    protected void build(Player viewer) {
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .filter(player -> !player.equals(viewer))
                .filter(player -> plugin.parties().partyOf(player).isEmpty())
                .filter(player -> !plugin.matches().isInMatch(player))
                .filter(player -> !plugin.matches().isSpectating(player))
                .filter(player -> !plugin.queue().isQueued(player))
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        if (candidates.isEmpty()) {
            set(layout().slotAt(12), Icons.of(Material.BARRIER,
                    Component.text("Nobody is available", NamedTextColor.RED),
                    Component.text("Players already in a party, in a queue", NamedTextColor.GRAY),
                    Component.text("or in a match are not shown.", NamedTextColor.GRAY)));
        }

        List<Player> shown = layout().pageItems(candidates, page);
        for (int i = 0; i < shown.size(); i++) {
            Player target = shown.get(i);

            set(layout().slotAt(i), Icons.head(target,
                            Component.text(target.getName(), NamedTextColor.WHITE),
                            Component.text("Click to invite", NamedTextColor.GREEN)),
                    event -> {
                        plugin.parties().invite(viewer, target);
                        new PartyMenu(plugin).open(viewer);
                    });
        }

        paginate(viewer, candidates.size());

        set(SLOT_BACK, Icons.of(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW)),
                event -> new PartyMenu(plugin).open(viewer));
    }

    private void paginate(Player viewer, int total) {
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, Component.text("Previous page", NamedTextColor.YELLOW)),
                    event -> {
                        page--;
                        refresh(viewer);
                    });
        }
        if (page < layout().pageCount(total) - 1) {
            set(53, Icons.of(Material.ARROW, Component.text("Next page", NamedTextColor.YELLOW)),
                    event -> {
                        page++;
                        refresh(viewer);
                    });
        }
    }
}

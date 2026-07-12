package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.rating.Division;
import fr.ayoub.pvp.domain.rating.DivisionLadder;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.storage.LeaderboardEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** The top players of one mode / format. */
public final class LeaderboardMenu extends Menu {

    private static final int TOP = 10;

    private final PvPEnginePlugin plugin;
    private final String modeId;
    private final String format;
    private final DivisionLadder ladder;

    private List<LeaderboardEntry> entries = new ArrayList<>();
    private boolean loading = true;

    public LeaderboardMenu(PvPEnginePlugin plugin, String modeId, String format) {
        super(Component.text("Top " + TOP + " — " + modeId + " " + format, NamedTextColor.GOLD),
                MenuLayout.bordered(4));
        this.plugin = plugin;
        this.modeId = modeId;
        this.format = format;
        this.ladder = plugin.matches().ratings().ladder();
    }

    @Override
    public void open(Player viewer) {
        super.open(viewer);
        load(viewer);
    }

    @Override
    protected void build(Player viewer) {
        if (loading) {
            set(layout().slotAt(0), Icons.of(Material.CLOCK, Component.text("Loading…", NamedTextColor.GRAY)));
            return;
        }

        if (entries.isEmpty()) {
            set(layout().slotAt(0), Icons.of(Material.BARRIER,
                    Component.text("Nobody has played yet", NamedTextColor.RED)));
            return;
        }

        for (int i = 0; i < entries.size() && i < layout().itemsPerPage(); i++) {
            LeaderboardEntry entry = entries.get(i);
            Division division = ladder.of(entry.rating());
            int rank = i + 1;

            boolean isViewer = entry.uuid().equals(viewer.getUniqueId());

            set(layout().slotAt(i), Icons.of(
                    isViewer ? Material.NETHER_STAR : Material.PLAYER_HEAD,
                    Component.text("#" + rank + " " + entry.username(),
                            isViewer ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    Component.text("Rating: ", NamedTextColor.GRAY)
                            .append(Component.text(entry.rating(), NamedTextColor.WHITE)),
                    Component.text("Division: ", NamedTextColor.GRAY)
                            .append(Component.text(division.display(), NamedTextColor.AQUA)),
                    Component.text("Record: ", NamedTextColor.GRAY)
                            .append(Component.text(entry.wins() + "W / " + entry.losses() + "L",
                                    NamedTextColor.WHITE))));
        }
    }

    private void load(Player viewer) {
        plugin.async().execute(() -> {
            List<LeaderboardEntry> found = plugin.ratings().top(modeId, format, TOP);

            Bukkit.getScheduler().runTask(plugin, () -> {
                entries = found;
                loading = false;
                if (viewer.isOnline()) {
                    refresh(viewer);
                }
            });
        });
    }
}

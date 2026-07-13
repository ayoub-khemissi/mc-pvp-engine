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

/**
 * The ladder of one mode / format.
 *
 * Only the page being looked at is read from the database — with ten thousand ranked
 * players, loading the whole ladder to show twenty-eight of them would be a slow query on
 * a thread the server waits on. The paging in {@link Menu} therefore has to re-fetch, which
 * is what {@link #onPageChanged} is for.
 */
public final class LeaderboardMenu extends Menu {

    private final PvPEnginePlugin plugin;
    private final String modeId;
    private final String format;
    private final DivisionLadder ladder;

    private List<LeaderboardEntry> entries = new ArrayList<>();
    private int total;
    private boolean loading = true;

    public LeaderboardMenu(PvPEnginePlugin plugin, String modeId, String format, Menu parent) {
        super(Component.text("Ladder — " + modeId + " " + format, NamedTextColor.GOLD),
                MenuLayout.bordered(6), parent);
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
    protected void onPageChanged(Player viewer) {
        loading = true;
        refresh(viewer);
        load(viewer);
    }

    @Override
    protected void build(Player viewer) {
        if (loading) {
            set(layout().slotAt(0), Icons.of(Material.CLOCK,
                    Component.text("Loading…", NamedTextColor.GRAY)));
            paginate(viewer, total);   // keep the buttons where they are while it loads
            return;
        }

        if (entries.isEmpty()) {
            set(layout().slotAt(0), Icons.of(Material.BARRIER,
                    Component.text("Nobody has played yet", NamedTextColor.RED)));
            return;
        }

        int firstRank = page() * layout().itemsPerPage() + 1;

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            Division division = ladder.of(entry.rating());
            int rank = firstRank + i;

            boolean isViewer = entry.uuid().equals(viewer.getUniqueId());

            set(layout().slotAt(i), Icons.head(Bukkit.getOfflinePlayer(entry.uuid()),
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

        paginate(viewer, total);
    }

    private void load(Player viewer) {
        int offset = page() * layout().itemsPerPage();
        int limit = layout().itemsPerPage();

        plugin.async().execute(() -> {
            List<LeaderboardEntry> found = plugin.ratings().page(modeId, format, offset, limit);
            int count = plugin.ratings().countRanked(modeId, format);

            Bukkit.getScheduler().runTask(plugin, () -> {
                entries = found;
                total = count;
                loading = false;
                if (viewer.isOnline()) {
                    refresh(viewer);
                }
            });
        });
    }
}

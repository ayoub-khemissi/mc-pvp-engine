package fr.ayoub.pvp.core.menu;

import fr.ayoub.pvp.core.PvPEnginePlugin;
import fr.ayoub.pvp.core.ui.Icons;
import fr.ayoub.pvp.core.ui.Menu;
import fr.ayoub.pvp.domain.rating.Division;
import fr.ayoub.pvp.domain.rating.DivisionLadder;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import fr.ayoub.pvp.storage.RatingEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Profile" — your rank in every mode you have played, and a way into the leaderboards.
 *
 * The database is read on a worker thread; the menu is only touched back on the main one.
 */
public final class ProfileMenu extends Menu {

    private final PvPEnginePlugin plugin;
    private final DivisionLadder ladder;

    private List<RatingEntry> ratings = new ArrayList<>();
    private boolean loading = true;

    public ProfileMenu(PvPEnginePlugin plugin) {
        super(Component.text("Profile", NamedTextColor.DARK_AQUA), MenuLayout.bordered(4));
        this.plugin = plugin;
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

        if (ratings.isEmpty()) {
            set(layout().slotAt(0), Icons.of(Material.PLAYER_HEAD,
                    Component.text(viewer.getName(), NamedTextColor.AQUA),
                    Component.text("No ranked games yet.", NamedTextColor.GRAY),
                    Component.text("Play a duel to get a rating.", NamedTextColor.DARK_GRAY)));
            return;
        }

        for (int i = 0; i < ratings.size() && i < layout().itemsPerPage(); i++) {
            RatingEntry entry = ratings.get(i);
            Division division = ladder.of(entry.row().rating());
            Optional<Division> next = ladder.next(entry.row().rating());

            String streak = streakOf(entry);

            set(layout().slotAt(i), Icons.of(Material.DIAMOND_SWORD,
                            Component.text(entry.modeId() + " " + entry.format(), NamedTextColor.GOLD),
                            Component.text("Rating: ", NamedTextColor.GRAY)
                                    .append(Component.text(entry.row().rating(), NamedTextColor.WHITE)),
                            Component.text("Division: ", NamedTextColor.GRAY)
                                    .append(Component.text(division.display(), NamedTextColor.AQUA)),
                            Component.text("Record: ", NamedTextColor.GRAY)
                                    .append(Component.text(entry.row().wins() + "W / " + entry.row().losses() + "L",
                                            NamedTextColor.WHITE)),
                            Component.text("Peak: ", NamedTextColor.GRAY)
                                    .append(Component.text(entry.row().peakRating(), NamedTextColor.WHITE)),
                            Component.text(streak, NamedTextColor.YELLOW),
                            next.map(division1 -> Component.text(
                                            "Next: " + division1.display() + " at " + division1.minRating(),
                                            NamedTextColor.DARK_GRAY))
                                    .orElse(Component.text("Top division", NamedTextColor.DARK_GRAY)),
                            Component.empty(),
                            Component.text("Click for the leaderboard", NamedTextColor.GREEN)),
                    event -> new LeaderboardMenu(plugin, entry.modeId(), entry.format()).open(viewer));
        }
    }

    private static String streakOf(RatingEntry entry) {
        int streak = entry.row().streak();
        if (streak > 0) {
            return streak + " win streak";
        }
        if (streak < 0) {
            return -streak + " loss streak";
        }
        return "No streak";
    }

    private void load(Player viewer) {
        plugin.async().execute(() -> {
            List<RatingEntry> found = plugin.ratings().findAllFor(viewer.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                ratings = found;
                loading = false;
                if (viewer.isOnline()) {
                    refresh(viewer);
                }
            });
        });
    }
}

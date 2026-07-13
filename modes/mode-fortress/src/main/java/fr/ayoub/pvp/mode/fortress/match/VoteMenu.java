package fr.ayoub.pvp.mode.fortress.match;

import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.fortress.Candidate;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The three fortresses, as a screen.
 *
 * Numbered the same way they are numbered on the floor outside — 1 on the left, 3 on the
 * right — because a player who has just flown through all three should not have to work out
 * which of them is which a second time.
 */
public final class VoteMenu extends Menu {

    private final VotePhase phase;

    public VoteMenu(VotePhase phase, Player viewer) {
        super(Component.text("Choose your fortress", NamedTextColor.GOLD), MenuLayout.bordered(3));
        this.phase = phase;
    }

    @Override
    protected void build(Player viewer) {
        List<Candidate> options = phase.optionsFor(viewer);
        Integer current = phase.voteOf(viewer);

        for (int i = 0; i < options.size() && i < 3; i++) {
            Candidate candidate = options.get(i);
            int number = i + 1;
            boolean mine = current != null && current == number;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(candidate.isPreset()
                    ? "A preset"
                    : "Built by " + nameOf(candidate), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(mine
                    ? Component.text("★ Your vote", NamedTextColor.GREEN)
                    : Component.text("Click to vote for it", NamedTextColor.YELLOW));

            set(layout().slotAt(i * 3 + 1), Icons.of(
                            mine ? Material.LIME_CONCRETE : Material.BRICKS,
                            Component.text(number + ". " + candidate.name(),
                                    mine ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                            lore.toArray(Component[]::new)),
                    event -> {
                        phase.cast(viewer, number);
                        viewer.closeInventory();
                    });
        }
    }

    private static String nameOf(Candidate candidate) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(candidate.owner());
        return owner.getName() != null ? owner.getName() : "a teammate";
    }
}

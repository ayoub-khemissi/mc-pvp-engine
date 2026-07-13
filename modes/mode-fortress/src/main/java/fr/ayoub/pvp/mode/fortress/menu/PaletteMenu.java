package fr.ayoub.pvp.mode.fortress.menu;

import fr.ayoub.pvp.api.ui.Icons;
import fr.ayoub.pvp.api.ui.Menu;
import fr.ayoub.pvp.domain.fortress.Blueprint;
import fr.ayoub.pvp.domain.fortress.BuildRules;
import fr.ayoub.pvp.domain.ui.MenuLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everything you are allowed to build with, and how much of it you have left.
 *
 * It looks like a chest, and it is deliberately <b>not</b> one. A real chest in a creative
 * zone gets emptied, refilled with junk, and the next builder inherits the mess. This is a
 * read-only screen: it cannot be taken from, and it can show something a chest never could
 * — the count that is left in the build you are working on <i>right now</i>.
 */
public final class PaletteMenu extends Menu {

    private final BuildRules rules;
    private final Blueprint blueprint;

    public PaletteMenu(BuildRules rules, Blueprint blueprint) {
        super(Component.text("Allowed blocks", NamedTextColor.DARK_AQUA), MenuLayout.bordered(6));
        this.rules = rules;
        this.blueprint = blueprint;
    }

    @Override
    protected void build(Player viewer) {
        // Hardest first — the same order as the hotbar, so the two screens agree.
        List<String> blocks = rules.allowance().keySet().stream()
                .sorted(Comparator
                        .comparingDouble((String id) -> blastOf(id)).reversed()
                        .thenComparing(id -> id))
                .toList();

        List<String> shown = pageItems(blocks);

        for (int i = 0; i < shown.size(); i++) {
            String id = shown.get(i);
            Material material = Material.matchMaterial(id);
            if (material == null) {
                continue;
            }

            int quota = rules.quota(id);
            int used = blueprint.counts().getOrDefault(id, 0);
            int left = rules.remaining(blueprint, id);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(left + " left", left == 0
                    ? NamedTextColor.RED : NamedTextColor.GREEN));
            lore.add(Component.text(used + " / " + quota + " used", NamedTextColor.GRAY));

            set(layout().slotAt(i), Icons.of(material,
                    Component.text(pretty(id), NamedTextColor.WHITE),
                    lore.toArray(Component[]::new)));
        }

        paginate(viewer, blocks.size());
    }

    private static float blastOf(String id) {
        Material material = Material.matchMaterial(id);
        return material == null ? 0f : material.getBlastResistance();
    }

    private static String pretty(String id) {
        return id.charAt(0) + id.substring(1).toLowerCase().replace('_', ' ');
    }
}

package fr.ayoub.pvp.mode.duel;

import fr.ayoub.pvp.api.GameModeDefinition;
import fr.ayoub.pvp.api.MatchHandler;
import fr.ayoub.pvp.api.MatchRules;
import fr.ayoub.pvp.domain.match.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Plain vanilla PvP: last team standing wins. */
public final class DuelMode implements GameModeDefinition {

    @Override
    public String id() {
        return "duel";
    }

    @Override
    public Component displayName() {
        return Component.text("Duel", NamedTextColor.GOLD);
    }

    @Override
    public ItemStack icon() {
        ItemStack icon = new ItemStack(Material.DIAMOND_SWORD);
        icon.editMeta(meta -> {
            meta.displayName(displayName().decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Vanilla PvP, last team standing.", NamedTextColor.GRAY)
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

    @Override
    public MatchRules rules() {
        return MatchRules.standard();   // 1 round, 5s countdown, 5 min limit
    }

    @Override
    public MatchHandler createHandler() {
        return new DuelHandler();
    }
}

package fr.ayoub.pvp.api.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

/** Builds menu icons without Minecraft's default purple italics. */
public final class Icons {

    private Icons() {
    }

    public static ItemStack of(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        List<Component> lines = Arrays.stream(lore)
                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                .toList();

        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!lines.isEmpty()) {
                meta.lore(lines);
            }
        });
        return item;
    }

    /** The same, but wearing the player's own skin — used everywhere we list people. */
    public static ItemStack head(OfflinePlayer owner, Component name, Component... lore) {
        ItemStack item = of(Material.PLAYER_HEAD, name, lore);
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(owner));
        return item;
    }
}

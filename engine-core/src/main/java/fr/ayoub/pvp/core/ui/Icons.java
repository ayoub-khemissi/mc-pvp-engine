package fr.ayoub.pvp.core.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
}

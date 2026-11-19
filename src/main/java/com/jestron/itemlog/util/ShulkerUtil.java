package com.jestron.itemlog.util;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shulker kutusu içeriğini lore satırlarına çevirir. */
public final class ShulkerUtil {

    private ShulkerUtil() {
    }

    public static boolean isShulker(Material m) {
        return m != null && m.name().endsWith("SHULKER_BOX");
    }

    public static List<String> contentsLore(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (item == null || !isShulker(item.getType())) return lines;
        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof BlockStateMeta)) return lines;
            BlockState state = ((BlockStateMeta) meta).getBlockState();
            if (!(state instanceof ShulkerBox)) return lines;

            lines.add("&8▼ Shulker içeriği:");
            int shown = 0;
            int hidden = 0;
            for (ItemStack content : ((ShulkerBox) state).getInventory().getContents()) {
                if (content == null || content.getType() == Material.AIR) continue;
                if (shown < 27) {
                    lines.add("  &7• &f" + Text.material(content.getType().name()) + " &8x&f" + content.getAmount());
                    shown++;
                } else {
                    hidden++;
                }
            }
            if (shown == 0) lines.add("  &8(boş)");
            if (hidden > 0) lines.add("  &8... +" + hidden);
        } catch (Throwable ignored) {
            // Bazı çatallarda BlockState okuma istisna atabilir; içeriği göstermeden devam et.
        }
        return lines;
    }
}

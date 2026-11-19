package com.jestron.itemlog.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** GUI item oluşturucu – sadece 1.16.5'ten beri değişmeyen API'ler. */
public final class Items {

    private Items() {
    }

    public static ItemStack make(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String line : lore) colored.add(Text.color(line));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack make(Material material, String name, String... lore) {
        return make(material, name, Arrays.asList(lore));
    }

    public static ItemStack filler(Material material) {
        return make(material, " ");
    }

    /** Kayıttaki materyal adını bu sunucu sürümünde güvenle çözer. */
    public static Material resolve(String materialName) {
        if (materialName == null) return Material.BARRIER;
        Material m = Material.getMaterial(materialName);
        if (m == null || m == Material.AIR || m.name().startsWith("LEGACY_") || !m.isItem()) return Material.BARRIER;
        return m;
    }

    public static int displayAmount(Material m, int amount) {
        int max = Math.max(1, m.getMaxStackSize());
        return Math.max(1, Math.min(amount, max));
    }
}

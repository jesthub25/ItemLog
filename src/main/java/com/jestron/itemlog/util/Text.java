package com.jestron.itemlog.util;

import org.bukkit.ChatColor;

import java.util.Locale;

/** Metin yardımcıları. Sadece legacy renk kodları kullanılır (tüm sürümlerde çalışır). */
public final class Text {

    private Text() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** NETHERITE_SWORD → Netherite Sword */
    public static String material(String name) {
        if (name == null || name.isEmpty()) return "Bilinmiyor";
        StringBuilder sb = new StringBuilder();
        for (String w : name.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String coords(double x, double y, double z) {
        return "X:" + (long) Math.floor(x) + " Y:" + (long) Math.floor(y) + " Z:" + (long) Math.floor(z);
    }

    /** Envanter başlıklarını güvenli uzunlukta tutar. */
    public static String title(String s) {
        String t = color(s);
        return t.length() <= 32 ? t : t.substring(0, 32);
    }

    public static String orUnknown(String s) {
        return s == null || s.trim().isEmpty() ? "Bilinmiyor" : s;
    }
}

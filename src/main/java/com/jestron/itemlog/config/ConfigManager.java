package com.jestron.itemlog.config;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** config.yml okuyucu. Sonuç her zaman değiştirilemez bir {@link Settings} nesnesidir. */
public final class ConfigManager {

    private final ItemLog plugin;
    private volatile Settings settings;

    public ConfigManager(ItemLog plugin) {
        this.plugin = plugin;
    }

    public Settings get() {
        return settings;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        Logger log = plugin.getLogger();

        Set<Material> materials = parseMaterials(c.getStringList("tracked-items"), log);
        List<TrackedZone> zones = parseZones(c.getMapList("tracked-zones"), log);

        String pattern = c.getString("settings.date-format", "dd/MM/yyyy HH:mm:ss");
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            log.warning("Geçersiz date-format '" + pattern + "', varsayılan kullanılıyor.");
            formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        }
        String tz = c.getString("settings.timezone", "system");
        ZoneId zone;
        try {
            zone = (tz == null || tz.trim().isEmpty() || tz.equalsIgnoreCase("system"))
                    ? ZoneId.systemDefault() : ZoneId.of(tz.trim());
        } catch (Exception e) {
            log.warning("Geçersiz timezone '" + tz + "', sistem saat dilimi kullanılıyor.");
            zone = ZoneId.systemDefault();
        }

        int retention = Math.max(0, c.getInt("settings.retention-days", 0));
        long refreshSeconds = Math.max(30L, c.getLong("settings.name-cache-refresh-seconds", 300L));

        settings = new Settings(
                materials,
                zones,
                c.getBoolean("settings.log-drops", true),
                c.getBoolean("settings.log-pickups", true),
                c.getBoolean("settings.log-deaths", true),
                c.getBoolean("settings.log-unlinked-pickups", true),
                c.getBoolean("settings.show-shulker-contents", true),
                retention,
                refreshSeconds * 20L,
                formatter.withZone(zone),
                Text.color(c.getString("messages.prefix", "&8[&5ItemLog&8] "))
        );

        log.info(materials.size() + " materyal takip ediliyor, "
                + (zones.isEmpty() ? "bölge filtresi yok (her yer)." : zones.size() + " bölge filtresi aktif."));
    }

    // ── Materyaller ─────────────────────────────────────────────────────────

    private static Set<Material> parseMaterials(List<String> entries, Logger log) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        List<String> missing = new ArrayList<>();

        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim().toUpperCase(Locale.ROOT);
            if (entry.startsWith("MINECRAFT:")) entry = entry.substring("MINECRAFT:".length());
            if (entry.isEmpty()) continue;

            if (entry.equals("ALL_SHULKER_BOXES")) entry = "*SHULKER_BOX";

            if (entry.indexOf('*') >= 0) {
                Pattern p = glob(entry);
                int before = result.size();
                for (Material m : Material.values()) {
                    if (!m.name().startsWith("LEGACY_") && p.matcher(m.name()).matches()) result.add(m);
                }
                if (result.size() == before) missing.add(raw.trim());
                continue;
            }

            Material m = Material.getMaterial(entry);
            if (m == null || m.name().startsWith("LEGACY_")) {
                missing.add(raw.trim());
            } else {
                result.add(m);
            }
        }

        if (!missing.isEmpty()) {
            log.info("Bu sunucu sürümünde bulunmayan/geçersiz materyaller atlandı: " + String.join(", ", missing));
        }
        if (result.isEmpty()) {
            log.warning("tracked-items listesi boş veya geçersiz — hiçbir item loglanmayacak!");
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(result);
    }

    private static Pattern glob(String expr) {
        StringBuilder sb = new StringBuilder();
        for (String part : expr.split("\\*", -1)) {
            if (sb.length() > 0) sb.append(".*");
            sb.append(Pattern.quote(part));
        }
        return Pattern.compile(sb.toString());
    }

    // ── Bölgeler ────────────────────────────────────────────────────────────

    private static List<TrackedZone> parseZones(List<Map<?, ?>> list, Logger log) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<TrackedZone> zones = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> m : list) {
            index++;
            if (m == null) continue;
            Object worldObj = m.get("world");
            String world = worldObj == null ? "" : String.valueOf(worldObj).trim();
            if (world.isEmpty()) {
                log.warning("tracked-zones #" + index + ": 'world' eksik, atlanıyor.");
                continue;
            }
            Object minXObj = m.get("min-x");
            boolean whole = minXObj == null && m.get("max-x") == null
                    || minXObj != null && "ALL".equalsIgnoreCase(String.valueOf(minXObj).trim());

            TrackedZone zone;
            if (whole) {
                zone = new TrackedZone(world, true, 0, 0, 0, 0, 0, 0);
            } else {
                zone = new TrackedZone(world, false,
                        num(m.get("min-x"), -30_000_000), num(m.get("max-x"), 30_000_000),
                        num(m.get("min-y"), -2048),       num(m.get("max-y"), 4096),
                        num(m.get("min-z"), -30_000_000), num(m.get("max-z"), 30_000_000));
            }
            zones.add(zone);
            log.info("Bölge: " + zone.describe());
        }
        return Collections.unmodifiableList(zones);
    }

    private static double num(Object raw, double fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

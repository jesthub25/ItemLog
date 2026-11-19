package com.jestron.itemlog.config;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Konfigürasyonun değiştirilemez anlık görüntüsü.
 * Reload sırasında yenisi oluşturulup tek seferde değiştirilir; bu sayede
 * async thread'ler hiçbir zaman yarım yüklenmiş veriyi görmez.
 */
public final class Settings {

    private final Set<Material>     trackedMaterials;
    private final List<TrackedZone> zones;
    private final boolean logDrops;
    private final boolean logPickups;
    private final boolean logDeaths;
    private final boolean logUnlinkedPickups;
    private final boolean showShulkerContents;
    private final int     retentionDays;
    private final long    nameCacheRefreshTicks;
    private final DateTimeFormatter dateFormatter;
    private final String  prefix;

    Settings(Set<Material> trackedMaterials, List<TrackedZone> zones,
             boolean logDrops, boolean logPickups, boolean logDeaths,
             boolean logUnlinkedPickups, boolean showShulkerContents,
             int retentionDays, long nameCacheRefreshTicks,
             DateTimeFormatter dateFormatter, String prefix) {
        this.trackedMaterials = trackedMaterials;
        this.zones = zones;
        this.logDrops = logDrops;
        this.logPickups = logPickups;
        this.logDeaths = logDeaths;
        this.logUnlinkedPickups = logUnlinkedPickups;
        this.showShulkerContents = showShulkerContents;
        this.retentionDays = retentionDays;
        this.nameCacheRefreshTicks = nameCacheRefreshTicks;
        this.dateFormatter = dateFormatter;
        this.prefix = prefix;
    }

    public boolean isTracked(Material m) {
        return m != null && trackedMaterials.contains(m);
    }

    public boolean isInZone(String world, double x, double y, double z) {
        if (zones.isEmpty()) return true;
        for (TrackedZone zone : zones) {
            if (zone.contains(world, x, y, z)) return true;
        }
        return false;
    }

    public boolean isInZone(Location loc) {
        if (loc == null) return false;
        World w = loc.getWorld();
        return w != null && isInZone(w.getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    public String formatDate(long epochMillis) {
        return dateFormatter.format(Instant.ofEpochMilli(epochMillis));
    }

    public Set<Material> getTrackedMaterials()  { return trackedMaterials; }
    public List<TrackedZone> getZones()         { return zones; }
    public boolean isLogDrops()                 { return logDrops; }
    public boolean isLogPickups()               { return logPickups; }
    public boolean isLogDeaths()                { return logDeaths; }
    public boolean isLogUnlinkedPickups()       { return logUnlinkedPickups; }
    public boolean isShowShulkerContents()      { return showShulkerContents; }
    public int  getRetentionDays()              { return retentionDays; }
    public long getNameCacheRefreshTicks()      { return nameCacheRefreshTicks; }
    public String getPrefix()                   { return prefix; }
}

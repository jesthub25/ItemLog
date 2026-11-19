package com.jestron.itemlog.config;

/** Dünya + (isteğe bağlı) koordinat kutusu. Değiştirilemez, thread-safe. */
public final class TrackedZone {

    private final String  world;
    private final boolean wholeWorld;
    private final double  minX, maxX, minY, maxY, minZ, maxZ;

    TrackedZone(String world, boolean wholeWorld,
                double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        this.world = world;
        this.wholeWorld = wholeWorld;
        this.minX = Math.min(minX, maxX); this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY); this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ); this.maxZ = Math.max(minZ, maxZ);
    }

    public boolean contains(String worldName, double x, double y, double z) {
        if (worldName == null || !world.equalsIgnoreCase(worldName)) return false;
        if (wholeWorld) return true;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String describe() {
        if (wholeWorld) return world + " (tamamı)";
        return world + " X[" + (long) minX + "~" + (long) maxX + "] Y[" + (long) minY + "~" + (long) maxY
                + "] Z[" + (long) minZ + "~" + (long) maxZ + "]";
    }
}

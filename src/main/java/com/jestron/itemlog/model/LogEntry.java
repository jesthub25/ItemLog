package com.jestron.itemlog.model;

/**
 * Tek bir log kaydı.
 *
 * event_type:
 *   DROP         – oyuncu itemi yere attı           (related = alan kişi)
 *   PICKUP       – oyuncu yerden aldı                (related = atan kişi, varsa)
 *   DEATH_DROP   – oyuncu öldü, item yere düştü      (related = alan kişi)
 *   DEATH_PICKUP – ölüm düşüşünden item alındı       (related = ölen kişi)
 */
public final class LogEntry {

    public static final String DROP         = "DROP";
    public static final String PICKUP       = "PICKUP";
    public static final String DEATH_DROP   = "DEATH_DROP";
    public static final String DEATH_PICKUP = "DEATH_PICKUP";

    private int    id;
    private String eventType;
    private String playerUUID;
    private String playerName;
    private String relatedUUID;
    private String relatedName;
    private String itemMaterial;
    private int    itemAmount;
    private String itemSerialized;
    private String world;
    private double locX;
    private double locY;
    private double locZ;
    private long   timestamp;
    private String entityUUID;
    private int    linkedId = -1;

    public boolean isDropType() {
        return DROP.equals(eventType) || DEATH_DROP.equals(eventType);
    }

    public boolean hasRelated() {
        return relatedName != null && !relatedName.trim().isEmpty();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPlayerUUID() { return playerUUID; }
    public void setPlayerUUID(String playerUUID) { this.playerUUID = playerUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getRelatedUUID() { return relatedUUID; }
    public void setRelatedUUID(String relatedUUID) { this.relatedUUID = relatedUUID; }

    public String getRelatedName() { return relatedName; }
    public void setRelatedName(String relatedName) { this.relatedName = relatedName; }

    public String getItemMaterial() { return itemMaterial; }
    public void setItemMaterial(String itemMaterial) { this.itemMaterial = itemMaterial; }

    public int getItemAmount() { return itemAmount; }
    public void setItemAmount(int itemAmount) { this.itemAmount = itemAmount; }

    public String getItemSerialized() { return itemSerialized; }
    public void setItemSerialized(String itemSerialized) { this.itemSerialized = itemSerialized; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getLocX() { return locX; }
    public void setLocX(double locX) { this.locX = locX; }

    public double getLocY() { return locY; }
    public void setLocY(double locY) { this.locY = locY; }

    public double getLocZ() { return locZ; }
    public void setLocZ(double locZ) { this.locZ = locZ; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getEntityUUID() { return entityUUID; }
    public void setEntityUUID(String entityUUID) { this.entityUUID = entityUUID; }

    public int getLinkedId() { return linkedId; }
    public void setLinkedId(int linkedId) { this.linkedId = linkedId; }
}

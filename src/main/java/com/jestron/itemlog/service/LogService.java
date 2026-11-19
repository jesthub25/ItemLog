package com.jestron.itemlog.service;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.config.Settings;
import com.jestron.itemlog.database.Database;
import com.jestron.itemlog.model.LogEntry;
import com.jestron.itemlog.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Tüm yazma işlemleri TEK bir arka plan thread'inde sırayla yürür.
 *  • Ana thread hiç beklemez (lag yok).
 *  • Atma kaydı her zaman alma kaydından önce yazılır → zincir kopmaz.
 *  • Kapanışta kuyruktaki tüm kayıtlar diske yazılır (veri kaybı yok).
 *
 * Listener'lar filtrelemeyi ana thread'de yapar; buraya sadece kaydedilecek
 * olaylar ve ItemStack'in kopyası gelir.
 */
public final class LogService {

    private final ItemLog plugin;
    private final Database db;
    private final ExecutorService writer;

    public LogService(ItemLog plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ItemLog-Writer");
            t.setDaemon(true);
            return t;
        });
    }

    private void submit(final Runnable task) {
        try {
            writer.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Log yazılırken hata", t);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Eklenti kapanıyor.
        }
    }

    // ── Olaylar ────────────────────────────────────────────────────────────

    /** DROP veya DEATH_DROP. Çağıran filtre + bölge kontrolünü yapmış olmalıdır. */
    public void logDrop(final String type, final String playerUUID, final String playerName, final String entityUUID,
                        final ItemStack stack, final String world, final double x, final double y, final double z) {
        final long now = System.currentTimeMillis();
        submit(() -> db.insert(build(type, playerUUID, playerName, stack, entityUUID, world, x, y, z, now)));
    }

    /**
     * Yerden alma. Bekleyen atma kaydı varsa bölge filtresine bakılmaksızın
     * zincir tamamlanır; yoksa sadece bölge içindeyse ve ayar açıksa loglanır.
     */
    public void logPickup(final String pickerUUID, final String pickerName, final String entityUUID,
                          final ItemStack stack, final String world, final double x, final double y, final double z) {
        final long now = System.currentTimeMillis();
        submit(() -> {
            List<LogEntry> drops = db.findPendingDrops(entityUUID);
            LogEntry pickup;

            if (drops.isEmpty()) {
                Settings s = plugin.getConfigManager().get();
                if (!s.isLogUnlinkedPickups() || !s.isInZone(world, x, y, z)) return;
                pickup = build(LogEntry.PICKUP, pickerUUID, pickerName, stack, entityUUID, world, x, y, z, now);
                db.insert(pickup);
                return;
            }

            LogEntry primary = drops.get(0);
            String type = LogEntry.DEATH_DROP.equals(primary.getEventType()) ? LogEntry.DEATH_PICKUP : LogEntry.PICKUP;
            pickup = build(type, pickerUUID, pickerName, stack, entityUUID, world, x, y, z, now);
            pickup.setRelatedUUID(primary.getPlayerUUID());
            pickup.setRelatedName(primary.getPlayerName());
            pickup.setLinkedId(primary.getId());

            int pickupId = db.insert(pickup);
            if (pickupId <= 0) return;
            for (LogEntry drop : drops) {
                db.linkDrop(drop.getId(), pickupId, pickerUUID, pickerName);
            }
        });
    }

    /** İki item entity'si birleştiğinde bekleyen kayıtları hedefe taşır. */
    public void reassignEntity(final String from, final String to) {
        submit(() -> db.reassignEntity(from, to));
    }

    public void purgeOld() {
        final int days = plugin.getConfigManager().get().getRetentionDays();
        if (days <= 0) return;
        final long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        submit(() -> {
            int removed = db.purgeBefore(cutoff);
            if (removed > 0) plugin.getLogger().info(days + " günden eski " + removed + " kayıt silindi.");
        });
    }

    /** Kuyruktaki tüm yazmaları bitirir; en fazla 15 sn bekler. */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Bazı loglar 15 sn içinde yazılamadı, kapanış zorlanıyor.");
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Yardımcı ───────────────────────────────────────────────────────────

    private static LogEntry build(String type, String uuid, String name, ItemStack stack, String entityUUID,
                                  String world, double x, double y, double z, long time) {
        LogEntry e = new LogEntry();
        e.setEventType(type);
        e.setPlayerUUID(uuid);
        e.setPlayerName(name);
        e.setItemMaterial(stack.getType().name());
        e.setItemAmount(stack.getAmount());
        e.setItemSerialized(ItemSerializer.serialize(stack));
        e.setEntityUUID(entityUUID);
        e.setWorld(world);
        e.setLocX(x);
        e.setLocY(y);
        e.setLocZ(z);
        e.setTimestamp(time);
        return e;
    }
}

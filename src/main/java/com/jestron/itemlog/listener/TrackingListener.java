package com.jestron.itemlog.listener;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.config.Settings;
import com.jestron.itemlog.model.LogEntry;
import com.jestron.itemlog.service.LogService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Atma / alma / ölüm olaylarını yakalar.
 * Tüm filtreler ana thread'de, ucuz kontrollerle yapılır; sadece takip edilen
 * itemler için kopya alınıp yazma kuyruğuna gönderilir.
 */
public final class TrackingListener implements Listener {

    private static final double DEATH_SCAN_RADIUS = 6.0D;

    private final ItemLog plugin;
    private final LogService logs;

    public TrackingListener(ItemLog plugin, LogService logs) {
        this.plugin = plugin;
        this.logs = logs;
    }

    // ── Atma ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Settings s = plugin.getConfigManager().get();
        if (!s.isLogDrops()) return;

        Item entity = event.getItemDrop();
        ItemStack stack = entity.getItemStack();
        if (!s.isTracked(stack.getType())) return;

        Location loc = entity.getLocation();
        if (!s.isInZone(loc)) return;

        Player p = event.getPlayer();
        logs.logDrop(LogEntry.DROP, p.getUniqueId().toString(), p.getName(), entity.getUniqueId().toString(),
                stack.clone(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    // ── Alma ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Settings s = plugin.getConfigManager().get();
        if (!s.isLogPickups()) return;

        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (!s.isTracked(stack.getType())) return;

        Player p = (Player) event.getEntity();
        ItemStack copy = stack.clone();
        int taken = stack.getAmount() - Math.max(0, event.getRemaining());
        if (taken > 0 && taken < stack.getAmount()) copy.setAmount(taken);

        Location loc = entity.getLocation();
        // Bölge kontrolü yazma thread'inde yapılır: bölge içinde atılmış bir item
        // bölge dışında alınırsa da zincir tamamlanmalı.
        logs.logPickup(p.getUniqueId().toString(), p.getName(), entity.getUniqueId().toString(),
                copy, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    // ── Birleşme (aynı itemler yerde tek yığına dönüşür) ───────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        Settings s = plugin.getConfigManager().get();
        if (!s.isTracked(event.getEntity().getItemStack().getType())) return;
        logs.reassignEntity(event.getEntity().getUniqueId().toString(), event.getTarget().getUniqueId().toString());
    }

    // ── Ölüm ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Settings s = plugin.getConfigManager().get();
        if (!s.isLogDeaths() || event.getKeepInventory()) return;

        final Player dead = event.getEntity();
        final Location deathLoc = dead.getLocation().clone();
        if (!s.isInZone(deathLoc)) return;

        final List<ItemStack> tracked = new ArrayList<>();
        for (ItemStack stack : event.getDrops()) {
            if (stack != null && s.isTracked(stack.getType())) tracked.add(stack.clone());
        }
        if (tracked.isEmpty()) return;

        final String uuid = dead.getUniqueId().toString();
        final String name = dead.getName();

        // Item entity'leri olay bittikten sonra aynı tick içinde oluşur; 1 tick sonra eşleştir.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = deathLoc.getWorld();
            if (world == null) return;

            List<Item> candidates = new ArrayList<>();
            for (Entity e : world.getNearbyEntities(deathLoc, DEATH_SCAN_RADIUS, DEATH_SCAN_RADIUS, DEATH_SCAN_RADIUS)) {
                if (e instanceof Item && e.isValid() && e.getTicksLived() <= 40) candidates.add((Item) e);
            }

            for (ItemStack stack : tracked) {
                Item match = null;
                for (Iterator<Item> it = candidates.iterator(); it.hasNext(); ) {
                    Item candidate = it.next();
                    ItemStack cs = candidate.getItemStack();
                    if (cs.getAmount() == stack.getAmount() && cs.isSimilar(stack)) {
                        match = candidate;
                        it.remove();
                        break;
                    }
                }
                Location l = match != null ? match.getLocation() : deathLoc;
                logs.logDrop(LogEntry.DEATH_DROP, uuid, name,
                        match != null ? match.getUniqueId().toString() : null,
                        stack, world.getName(), l.getX(), l.getY(), l.getZ());
            }
        });
    }
}

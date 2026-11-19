package com.jestron.itemlog.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI olayları. InventoryView kullanılmaz (1.21'de sınıftan arayüze dönüştü ve
 * eski sürümlere göre derlenmiş eklentilerde IncompatibleClassChangeError verir);
 * bunun yerine event.getInventory() (üst envanter) ve holder kontrolü kullanılır.
 */
public final class GUIListener implements Listener {

    private final GUIManager gui;

    public GUIListener(GUIManager gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof ItemLogHolder)) return;

        // Shift-tık, sayı tuşu, çift tık dahil her türlü item hareketini engelle.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) return;   // alt (oyuncu) envanteri

        ItemLogHolder holder = (ItemLogHolder) top.getHolder();
        GUISession session = holder.getSession();
        if (gui.getSession(player.getUniqueId()) != session) {
            player.closeInventory();
            return;
        }
        if (!session.tryClick()) return;

        gui.handleClick(player, session, holder.getScreen(), raw);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ItemLogHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof ItemLogHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        gui.onInventoryClosed((Player) event.getPlayer(), ((ItemLogHolder) inv.getHolder()).getSession(), inv);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        gui.removeSession(event.getPlayer().getUniqueId());
    }
}

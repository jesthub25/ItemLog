package com.jestron.itemlog.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GUI'lerimizi başlık metnine bakmadan tanımak için özel holder.
 * Başlık kontrolü renk kodları, dil ve sürüme göre bozulabilir; holder bozulmaz.
 */
public final class ItemLogHolder implements InventoryHolder {

    private final GUISession session;
    private final GUISession.Screen screen;
    private Inventory inventory;

    ItemLogHolder(GUISession session, GUISession.Screen screen) {
        this.session = session;
        this.screen = screen;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public GUISession getSession() {
        return session;
    }

    public GUISession.Screen getScreen() {
        return screen;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

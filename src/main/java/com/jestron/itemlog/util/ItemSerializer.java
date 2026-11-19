package com.jestron.itemlog.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * ItemStack ↔ Base64. Bukkit serileştirmesi DataVersion bilgisini taşır;
 * eski sürümde kaydedilen item yeni sürümde otomatik dönüştürülür.
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    @SuppressWarnings("deprecation")
    public static String serialize(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(baos)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bais)) {
            Object o = in.readObject();
            return o instanceof ItemStack ? (ItemStack) o : null;
        } catch (Throwable t) {
            return null;
        }
    }
}

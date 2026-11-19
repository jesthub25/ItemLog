package com.jestron.itemlog.gui;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.config.Settings;
import com.jestron.itemlog.database.Database;
import com.jestron.itemlog.model.LogEntry;
import com.jestron.itemlog.util.ItemSerializer;
import com.jestron.itemlog.util.Items;
import com.jestron.itemlog.util.ShulkerUtil;
import com.jestron.itemlog.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tüm GUI ekranları. Kurallar:
 *  • Envanter açma/çizme sadece ana thread'de.
 *  • Veritabanı sadece async thread'de; sonuç ana thread'e geri gönderilir.
 *  • Sayfadaki kayıtlar oturumda tutulur → tıklamada tekrar sorgu yok,
 *    arada yeni log gelse bile yanlış kayıt açılmaz.
 */
public final class GUIManager {

    public static final int PAGE_SIZE = 45;

    // Liste ekranı (54)
    private static final int SLOT_PREV = 45, SLOT_FILTER = 47, SLOT_INFO = 49, SLOT_REFRESH = 51, SLOT_NEXT = 53;
    // Filtre ekranı (27)
    private static final int F_DROP = 10, F_PICKUP = 12, F_DEATH_DROP = 14, F_DEATH_PICKUP = 16, F_CLEAR = 18, F_APPLY = 22;
    // Detay ekranı (36)
    private static final int D_INFO = 4, D_ITEM = 13, D_TELEPORT = 20, D_LINKED = 22, D_GIVE = 24, D_BACK = 31;

    private final ItemLog plugin;
    private final Map<UUID, GUISession> sessions = new HashMap<>();

    public GUIManager(ItemLog plugin) {
        this.plugin = plugin;
    }

    // ── Oturumlar ──────────────────────────────────────────────────────────

    public GUISession getSession(UUID viewer) {
        return sessions.get(viewer);
    }

    public void removeSession(UUID viewer) {
        sessions.remove(viewer);
    }

    void onInventoryClosed(Player player, GUISession session, Inventory closed) {
        // Yeni ekran açılırken eskisinin kapanma olayı da gelir; sadece güncel ekran kapanınca oturumu bitir.
        if (sessions.get(player.getUniqueId()) == session && session.getCurrentInventory() == closed) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        sessions.clear();
    }

    private boolean isActive(Player viewer, GUISession s) {
        return viewer.isOnline() && sessions.get(viewer.getUniqueId()) == s;
    }

    // ── Giriş noktaları ────────────────────────────────────────────────────

    public void openPlayerLog(Player viewer, UUID target, String targetName) {
        GUISession s = new GUISession(viewer.getUniqueId(), GUISession.Mode.PLAYER, target, targetName, null, null);
        sessions.put(viewer.getUniqueId(), s);
        loadList(viewer, s);
    }

    public void openTrade(Player viewer, UUID a, String aName, UUID b, String bName) {
        GUISession s = new GUISession(viewer.getUniqueId(), GUISession.Mode.TRADE, a, aName, b, bName);
        sessions.put(viewer.getUniqueId(), s);
        loadList(viewer, s);
    }

    // ── Liste ──────────────────────────────────────────────────────────────

    private void loadList(final Player viewer, final GUISession s) {
        if (s.isLoading()) return;
        s.setLoading(true);

        final Database db = plugin.getDatabase();
        final int requestedPage = s.getPage();
        final List<String> filters = s.filterList();
        final String a = s.getTargetUUID().toString();
        final String b = s.getOtherUUID() == null ? null : s.getOtherUUID().toString();
        final boolean trade = s.getMode() == GUISession.Mode.TRADE;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int total = 0;
            int page = 0;
            int pages = 1;
            List<LogEntry> rows = Collections.emptyList();
            try {
                total = trade ? db.countTradeLogs(a, b) : db.countPlayerLogs(a, filters);
                pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
                page = Math.min(requestedPage, pages - 1);
                rows = trade ? db.getTradeLogs(a, b, PAGE_SIZE, page * PAGE_SIZE)
                             : db.getPlayerLogs(a, filters, PAGE_SIZE, page * PAGE_SIZE);
            } catch (Throwable t) {
                plugin.getLogger().warning("GUI verisi yüklenemedi: " + t.getMessage());
            }
            final int fTotal = total, fPage = page, fPages = pages;
            final List<LogEntry> fRows = rows;
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.setLoading(false);
                if (!isActive(viewer, s)) return;
                s.setTotal(fTotal);
                s.setTotalPages(fPages);
                s.setPage(fPage);
                s.setEntries(fRows);
                renderList(viewer, s);
            });
        });
    }

    private void renderList(Player viewer, GUISession s) {
        boolean trade = s.getMode() == GUISession.Mode.TRADE;
        String title = trade
                ? "&8Takas » " + s.getTargetName() + "↔" + s.getOtherName()
                : "&8ItemLog » " + s.getTargetName();
        Settings set = plugin.getConfigManager().get();

        ItemLogHolder holder = new ItemLogHolder(s, GUISession.Screen.LIST);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.title(title));
        holder.setInventory(inv);

        List<LogEntry> rows = s.getEntries();
        String targetUUID = s.getTargetUUID().toString();
        for (int i = 0; i < rows.size() && i < PAGE_SIZE; i++) {
            LogEntry e = rows.get(i);
            inv.setItem(i, trade ? tradeRow(e, set) : logRow(e, targetUUID, s.getTargetName(), set));
        }

        ItemStack bar = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) inv.setItem(i, bar);

        int page = s.getPage(), pages = s.getTotalPages();
        if (page > 0) {
            inv.setItem(SLOT_PREV, Items.make(Material.ARROW, "&c◄ Önceki Sayfa", "&7Sayfa " + page + " / " + pages));
        }
        if (page + 1 < pages) {
            inv.setItem(SLOT_NEXT, Items.make(Material.ARROW, "&a► Sonraki Sayfa", "&7Sayfa " + (page + 2) + " / " + pages));
        }
        if (!trade) {
            boolean active = !s.getFilters().isEmpty();
            List<String> lore = new ArrayList<>();
            lore.add(active ? "&aAktif filtre:" : "&7Log türüne göre filtrele");
            for (String f : s.getFilters()) lore.add("  &f• " + label(f));
            lore.add("");
            lore.add("&e▶ Tıkla");
            inv.setItem(SLOT_FILTER, Items.make(Material.HOPPER, (active ? "&6" : "&7") + "▼ Filtrele", lore));
        }

        List<String> info = new ArrayList<>();
        if (trade) {
            info.add("&7Oyuncular: &f" + s.getTargetName() + " &8↔ &f" + s.getOtherName());
        } else {
            info.add("&7Oyuncu: &f" + s.getTargetName());
        }
        info.add("&7Toplam kayıt: &f" + s.getTotal());
        info.add("&7Sayfa: &f" + (page + 1) + " / " + pages);
        if (s.getTotal() == 0) {
            info.add("");
            info.add("&8Gösterilecek kayıt yok.");
        }
        inv.setItem(SLOT_INFO, Items.make(Material.BOOK, "&6ℹ Bilgi", info));
        inv.setItem(SLOT_REFRESH, Items.make(Material.SUNFLOWER, "&e⟳ Yenile", "&7Listeyi veritabanından yeniden yükle"));

        show(viewer, s, GUISession.Screen.LIST, inv);
    }

    private ItemStack logRow(LogEntry e, String targetUUID, String targetName, Settings set) {
        Material m = Items.resolve(e.getItemMaterial());
        List<String> lore = new ArrayList<>();
        lore.add("&8────────────────────");
        lore.add("&7Tarih: &f" + set.formatDate(e.getTimestamp()));
        lore.add("&7Konum: &f" + Text.coords(e.getLocX(), e.getLocY(), e.getLocZ()) + " &8[" + e.getWorld() + "]");
        lore.add("&7Aktör: &f" + e.getPlayerName());
        if (e.hasRelated()) lore.add("&7İlgili: &f" + e.getRelatedName());
        lore.add(describe(e, targetUUID, targetName));
        lore.add("&8────────────────────");
        lore.add("&e▶ Tıkla → Detay");

        ItemStack item = Items.make(m, label(e.getEventType()) + " &8│ &f" + Text.material(e.getItemMaterial())
                + " &8x&f" + e.getItemAmount(), lore);
        item.setAmount(Items.displayAmount(m, e.getItemAmount()));
        return item;
    }

    private ItemStack tradeRow(LogEntry e, Settings set) {
        Material m = Items.resolve(e.getItemMaterial());
        String receiver = e.hasRelated() ? e.getRelatedName() : "(henüz alınmadı)";
        List<String> lore = new ArrayList<>();
        lore.add("&8────────────────────");
        lore.add("&7Tarih:   &f" + set.formatDate(e.getTimestamp()));
        lore.add("&7Konum:   &f" + Text.coords(e.getLocX(), e.getLocY(), e.getLocZ()) + " &8[" + e.getWorld() + "]");
        lore.add("&7Veren:   &e" + e.getPlayerName());
        lore.add("&7Alan:    &a" + receiver);
        lore.add("&7Yöntem:  " + (LogEntry.DEATH_DROP.equals(e.getEventType()) ? "&cÖlüm düşüşü" : "&eYere atma"));
        lore.add("&8────────────────────");
        lore.add("&e▶ Tıkla → Detay");

        ItemStack item = Items.make(m, "&f" + e.getPlayerName() + " &8→ &f" + receiver + " &8│ &f"
                + Text.material(e.getItemMaterial()) + " &8x&f" + e.getItemAmount(), lore);
        item.setAmount(Items.displayAmount(m, e.getItemAmount()));
        return item;
    }

    // ── Filtre ─────────────────────────────────────────────────────────────

    private void renderFilter(Player viewer, GUISession s) {
        ItemLogHolder holder = new ItemLogHolder(s, GUISession.Screen.FILTER);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.title("&8ItemLog » Filtre"));
        holder.setInventory(inv);

        ItemStack bg = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(F_DROP,         toggle(s, LogEntry.DROP,         Material.YELLOW_DYE));
        inv.setItem(F_PICKUP,       toggle(s, LogEntry.PICKUP,       Material.LIME_DYE));
        inv.setItem(F_DEATH_DROP,   toggle(s, LogEntry.DEATH_DROP,   Material.RED_DYE));
        inv.setItem(F_DEATH_PICKUP, toggle(s, LogEntry.DEATH_PICKUP, Material.ORANGE_DYE));
        inv.setItem(F_CLEAR, Items.make(Material.BARRIER, "&c✖ Filtreleri Temizle", "&7Tüm kayıt türlerini göster"));
        inv.setItem(F_APPLY, Items.make(Material.ARROW, "&a✔ Uygula ve Dön", "&7Listeye geri dön"));

        show(viewer, s, GUISession.Screen.FILTER, inv);
    }

    private ItemStack toggle(GUISession s, String type, Material on) {
        boolean active = s.getFilters().contains(type);
        return Items.make(active ? on : Material.GRAY_DYE,
                (active ? "&a✔ " : "&7✘ ") + label(type),
                active ? "&aAktif &7— kapatmak için tıkla" : "&7Kapalı — açmak için tıkla");
    }

    // ── Detay ──────────────────────────────────────────────────────────────

    private void renderDetail(Player viewer, GUISession s, LogEntry e) {
        s.setDetail(e);
        Settings set = plugin.getConfigManager().get();

        ItemLogHolder holder = new ItemLogHolder(s, GUISession.Screen.DETAIL);
        Inventory inv = Bukkit.createInventory(holder, 36, Text.title("&8ItemLog » Kayıt #" + e.getId()));
        holder.setInventory(inv);

        ItemStack bg = Items.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        inv.setItem(D_INFO, Items.make(Material.BOOK, "&6📖 Kayıt Bilgileri",
                label(e.getEventType()),
                "&7ID: &f#" + e.getId(),
                "&7Tarih: &f" + set.formatDate(e.getTimestamp()),
                "&7Oyuncu: &f" + e.getPlayerName(),
                "&7İlgili: &f" + Text.orUnknown(e.getRelatedName()),
                "&7Item: &f" + Text.material(e.getItemMaterial()) + " x" + e.getItemAmount(),
                "&7Dünya: &f" + e.getWorld(),
                "&7Konum: &f" + Text.coords(e.getLocX(), e.getLocY(), e.getLocZ()),
                e.getLinkedId() > 0 ? "&7Bağlı kayıt: &f#" + e.getLinkedId() : "&8Bağlı kayıt yok"));

        inv.setItem(D_ITEM, displayItem(e, set));

        inv.setItem(D_TELEPORT, Items.make(Material.ENDER_PEARL, "&b✦ Konuma Işınlan",
                "&7Dünya: &f" + e.getWorld(),
                "&f" + Text.coords(e.getLocX(), e.getLocY(), e.getLocZ()),
                "",
                viewer.hasPermission("itemlog.teleport") ? "&e▶ Tıkla" : "&cYetki yok (itemlog.teleport)"));

        if (e.getLinkedId() > 0) {
            inv.setItem(D_LINKED, Items.make(Material.TRIPWIRE_HOOK, "&d⛓ Bağlı Kaydı Aç",
                    "&7Kayıt &f#" + e.getLinkedId(),
                    e.isDropType() ? "&8(alış kaydı)" : "&8(atma / düşüş kaydı)",
                    "",
                    "&e▶ Tıkla"));
        }

        inv.setItem(D_GIVE, Items.make(Material.NETHER_STAR, "&e★ Kopyasını Al",
                "&7Itemin birebir kopyası envanterine eklenir",
                "",
                viewer.hasPermission("itemlog.give") ? "&e▶ Tıkla" : "&cYetki yok (itemlog.give)"));

        inv.setItem(D_BACK, Items.make(Material.ARROW, "&c◄ Geri", "&7Listeye dön"));

        show(viewer, s, GUISession.Screen.DETAIL, inv);
    }

    private ItemStack displayItem(LogEntry e, Settings set) {
        ItemStack item = ItemSerializer.deserialize(e.getItemSerialized());
        if (item == null || item.getType() == Material.AIR) {
            Material m = Items.resolve(e.getItemMaterial());
            item = new ItemStack(m, Items.displayAmount(m, e.getItemAmount()));
        } else {
            item = item.clone();
        }
        if (set.isShowShulkerContents() && ShulkerUtil.isShulker(item.getType())) {
            List<String> extra = ShulkerUtil.contentsLore(item);
            ItemMeta meta = item.getItemMeta();
            if (meta != null && !extra.isEmpty()) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
                for (String l : extra) lore.add(Text.color(l));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    // ── Tıklamalar ─────────────────────────────────────────────────────────

    void handleClick(Player p, GUISession s, GUISession.Screen screen, int slot) {
        if (s.isLoading()) return;
        switch (screen) {
            case LIST:
                clickList(p, s, slot);
                break;
            case FILTER:
                clickFilter(p, s, slot);
                break;
            case DETAIL:
                clickDetail(p, s, slot);
                break;
            default:
                break;
        }
    }

    private void clickList(Player p, GUISession s, int slot) {
        if (slot < PAGE_SIZE) {
            List<LogEntry> rows = s.getEntries();
            if (slot < rows.size()) renderDetail(p, s, rows.get(slot));
            return;
        }
        switch (slot) {
            case SLOT_PREV:
                if (s.getPage() > 0) {
                    s.setPage(s.getPage() - 1);
                    loadList(p, s);
                }
                break;
            case SLOT_NEXT:
                if (s.getPage() + 1 < s.getTotalPages()) {
                    s.setPage(s.getPage() + 1);
                    loadList(p, s);
                }
                break;
            case SLOT_FILTER:
                if (s.getMode() == GUISession.Mode.PLAYER) renderFilter(p, s);
                break;
            case SLOT_REFRESH:
                loadList(p, s);
                break;
            default:
                break;
        }
    }

    private void clickFilter(Player p, GUISession s, int slot) {
        switch (slot) {
            case F_DROP:
                s.toggleFilter(LogEntry.DROP);
                renderFilter(p, s);
                break;
            case F_PICKUP:
                s.toggleFilter(LogEntry.PICKUP);
                renderFilter(p, s);
                break;
            case F_DEATH_DROP:
                s.toggleFilter(LogEntry.DEATH_DROP);
                renderFilter(p, s);
                break;
            case F_DEATH_PICKUP:
                s.toggleFilter(LogEntry.DEATH_PICKUP);
                renderFilter(p, s);
                break;
            case F_CLEAR:
                s.getFilters().clear();
                s.setPage(0);
                loadList(p, s);
                break;
            case F_APPLY:
                s.setPage(0);
                loadList(p, s);
                break;
            default:
                break;
        }
    }

    private void clickDetail(final Player p, final GUISession s, int slot) {
        final LogEntry e = s.getDetail();
        if (e == null) return;
        switch (slot) {
            case D_TELEPORT:
                teleport(p, e);
                break;
            case D_GIVE:
                give(p, e);
                break;
            case D_LINKED:
                if (e.getLinkedId() <= 0) break;
                s.setLoading(true);
                final int linkedId = e.getLinkedId();
                final Database db = plugin.getDatabase();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    final LogEntry linked = db.getById(linkedId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        s.setLoading(false);
                        if (!isActive(p, s)) return;
                        if (linked == null) {
                            msg(p, "&cBağlı kayıt bulunamadı (silinmiş olabilir).");
                            return;
                        }
                        renderDetail(p, s, linked);
                    });
                });
                break;
            case D_BACK:
                renderList(p, s);
                break;
            default:
                break;
        }
    }

    private void teleport(Player p, LogEntry e) {
        if (!p.hasPermission("itemlog.teleport")) {
            msg(p, "&cBu işlem için yetkiniz yok. &7(itemlog.teleport)");
            return;
        }
        World w = Bukkit.getWorld(e.getWorld());
        if (w == null) {
            msg(p, "&cDünya bulunamadı veya yüklü değil: &f" + e.getWorld());
            return;
        }
        Location loc = new Location(w, e.getLocX(), e.getLocY(), e.getLocZ(), p.getLocation().getYaw(), p.getLocation().getPitch());
        p.closeInventory();
        if (p.teleport(loc)) {
            msg(p, "&a✦ Işınlandın: &f" + e.getWorld() + " &7(" + Text.coords(e.getLocX(), e.getLocY(), e.getLocZ()) + ")");
        } else {
            msg(p, "&cIşınlanma başka bir eklenti tarafından engellendi.");
        }
    }

    private void give(Player p, LogEntry e) {
        if (!p.hasPermission("itemlog.give")) {
            msg(p, "&cBu işlem için yetkiniz yok. &7(itemlog.give)");
            return;
        }
        ItemStack item = ItemSerializer.deserialize(e.getItemSerialized());
        if (item == null || item.getType() == Material.AIR) {
            msg(p, "&cItem verisi okunamadı (bu sürümde desteklenmiyor olabilir).");
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(item.clone());
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        msg(p, "&a★ Kopya eklendi: &f" + Text.material(item.getType().name()) + " x" + item.getAmount()
                + (left.isEmpty() ? "" : " &7(envanter dolu, kalanı yere bırakıldı)"));
        plugin.getLogger().info(p.getName() + " kayıt #" + e.getId() + " için item kopyası aldı ("
                + item.getType().name() + " x" + item.getAmount() + ").");
    }

    // ── Ortak ──────────────────────────────────────────────────────────────

    private void show(Player viewer, GUISession s, GUISession.Screen screen, Inventory inv) {
        s.setScreen(screen);
        s.setCurrentInventory(inv);   // eski ekranın kapanma olayından ÖNCE ayarlanmalı
        viewer.openInventory(inv);
    }

    private void msg(Player p, String text) {
        p.sendMessage(plugin.getConfigManager().get().getPrefix() + Text.color(text));
    }

    static String label(String type) {
        if (LogEntry.DROP.equals(type)) return "&eYere Atma";
        if (LogEntry.PICKUP.equals(type)) return "&aYerden Alma";
        if (LogEntry.DEATH_DROP.equals(type)) return "&cÖlüm Düşüşü";
        if (LogEntry.DEATH_PICKUP.equals(type)) return "&6Ölüm Sonrası Alma";
        return "&7Bilinmiyor";
    }

    /** Görüntülenen oyuncunun bakış açısından olay açıklaması. */
    private static String describe(LogEntry e, String targetUUID, String target) {
        boolean self = targetUUID.equals(e.getPlayerUUID());
        String actor = e.getPlayerName();
        String rel = e.hasRelated() ? e.getRelatedName() : null;
        String type = e.getEventType();

        if (LogEntry.DROP.equals(type)) {
            if (!self) return "&a" + target + " aldı &8← &e" + actor + " atmıştı";
            return rel == null ? "&e" + actor + " attı &7(henüz alınmadı)" : "&e" + actor + " attı &8→ &a" + rel + " aldı";
        }
        if (LogEntry.PICKUP.equals(type)) {
            if (!self) return "&e" + target + " attı &8→ &a" + actor + " aldı";
            return rel == null ? "&a" + actor + " yerden aldı" : "&a" + actor + " aldı &8← &e" + rel + " atmıştı";
        }
        if (LogEntry.DEATH_DROP.equals(type)) {
            if (!self) return "&6" + target + " aldı &8← &c" + actor + " ölüsünden";
            return rel == null ? "&c" + actor + " öldü, düştü &7(henüz alınmadı)" : "&c" + actor + " öldü &8→ &6" + rel + " aldı";
        }
        if (LogEntry.DEATH_PICKUP.equals(type)) {
            if (!self) return "&c" + target + " öldü &8→ &6" + actor + " aldı";
            return "&6" + actor + " aldı &8← &c" + Text.orUnknown(rel) + " ölüsünden";
        }
        return "&7Bilinmeyen işlem";
    }
}

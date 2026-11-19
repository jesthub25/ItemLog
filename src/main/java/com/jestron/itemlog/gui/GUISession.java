package com.jestron.itemlog.gui;

import com.jestron.itemlog.model.LogEntry;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bir yetkilinin açık GUI durumu. Sadece ana thread'de değiştirilir. */
public final class GUISession {

    public enum Mode { PLAYER, TRADE }

    public enum Screen { LIST, FILTER, DETAIL }

    private final UUID viewer;
    private final Mode mode;
    private final UUID targetUUID;
    private final String targetName;
    private final UUID otherUUID;     // sadece TRADE
    private final String otherName;   // sadece TRADE

    private final Set<String> filters = new LinkedHashSet<>();
    private int page;
    private int totalPages = 1;
    private int total;
    private List<LogEntry> entries = Collections.emptyList();
    private LogEntry detail;
    private Screen screen = Screen.LIST;
    private Inventory currentInventory;
    private boolean loading;
    private long lastClick;

    GUISession(UUID viewer, Mode mode, UUID targetUUID, String targetName, UUID otherUUID, String otherName) {
        this.viewer = viewer;
        this.mode = mode;
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.otherUUID = otherUUID;
        this.otherName = otherName;
    }

    /** Çift tıklama / spam koruması. */
    boolean tryClick() {
        long now = System.currentTimeMillis();
        if (now - lastClick < 150L) return false;
        lastClick = now;
        return true;
    }

    void toggleFilter(String type) {
        if (!filters.remove(type)) filters.add(type);
    }

    List<String> filterList() {
        return new ArrayList<>(filters);
    }

    public UUID getViewer()            { return viewer; }
    public Mode getMode()              { return mode; }
    public UUID getTargetUUID()        { return targetUUID; }
    public String getTargetName()      { return targetName; }
    public UUID getOtherUUID()         { return otherUUID; }
    public String getOtherName()       { return otherName; }
    public Set<String> getFilters()    { return filters; }
    public int getPage()               { return page; }
    void setPage(int page)             { this.page = Math.max(0, page); }
    public int getTotalPages()         { return totalPages; }
    void setTotalPages(int t)          { this.totalPages = Math.max(1, t); }
    public int getTotal()              { return total; }
    void setTotal(int total)           { this.total = total; }
    public List<LogEntry> getEntries() { return entries; }
    void setEntries(List<LogEntry> e)  { this.entries = e == null ? Collections.<LogEntry>emptyList() : e; }
    public LogEntry getDetail()        { return detail; }
    void setDetail(LogEntry d)         { this.detail = d; }
    public Screen getScreen()          { return screen; }
    void setScreen(Screen s)           { this.screen = s; }
    Inventory getCurrentInventory()    { return currentInventory; }
    void setCurrentInventory(Inventory i) { this.currentInventory = i; }
    boolean isLoading()                { return loading; }
    void setLoading(boolean l)         { this.loading = l; }
}

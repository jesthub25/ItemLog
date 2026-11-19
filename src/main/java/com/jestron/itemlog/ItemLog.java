package com.jestron.itemlog;

import com.jestron.itemlog.command.ItemLogCommand;
import com.jestron.itemlog.config.ConfigManager;
import com.jestron.itemlog.database.Database;
import com.jestron.itemlog.gui.GUIListener;
import com.jestron.itemlog.gui.GUIManager;
import com.jestron.itemlog.listener.TrackingListener;
import com.jestron.itemlog.service.LogService;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * ItemLog — Spigot / Paper / Purpur 1.16.5 – 1.21.x
 *
 * Açılış sırası: config → veritabanı → servis → GUI → listener → komut → görevler.
 */
public final class ItemLog extends JavaPlugin {

    private static final int NAME_CACHE_LIMIT = 10_000;
    private static final long RETENTION_INTERVAL_TICKS = 20L * 60L * 60L * 6L; // 6 saat

    private ConfigManager configManager;
    private Database database;
    private LogService logService;
    private GUIManager guiManager;

    private volatile List<String> nameCache = Collections.emptyList();
    private BukkitTask nameCacheTask;
    private BukkitTask retentionTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        try {
            configManager.reload();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "config.yml okunamadı, eklenti kapatılıyor.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = Database.create(this);
        try {
            database.connect();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Veritabanına bağlanılamadı (" + database.getTypeName()
                    + "): " + t.getMessage() + " — eklenti kapatılıyor.", t);
            database.close();
            database = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logService = new LogService(this, database);
        guiManager = new GUIManager(this);

        getServer().getPluginManager().registerEvents(new TrackingListener(this, logService), this);
        getServer().getPluginManager().registerEvents(new GUIListener(guiManager), this);

        ItemLogCommand command = new ItemLogCommand(this);
        bind("itemlog", command, command);
        bind("itemlogreload", command, command);

        scheduleTasks();
        logService.purgeOld();

        getLogger().info("ItemLog v" + getDescription().getVersion() + " etkin — sunucu: "
                + getServer().getName() + " " + getServer().getBukkitVersion());
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (guiManager != null) guiManager.closeAll();
        if (logService != null) logService.shutdown();   // kuyruktaki tüm loglar yazılır
        if (database != null) database.close();
        getLogger().info("ItemLog devre dışı.");
    }

    /** /itemlog reload */
    public void reloadPlugin() {
        configManager.reload();
        scheduleTasks();
        logService.purgeOld();
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("plugin.yml içinde '" + name + "' komutu yok!");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(completer);
    }

    private void scheduleTasks() {
        cancelTasks();
        long refresh = configManager.get().getNameCacheRefreshTicks();
        nameCacheTask = getServer().getScheduler()
                .runTaskTimerAsynchronously(this, this::refreshNameCache, 40L, refresh);
        retentionTask = getServer().getScheduler()
                .runTaskTimer(this, () -> logService.purgeOld(), RETENTION_INTERVAL_TICKS, RETENTION_INTERVAL_TICKS);
    }

    private void cancelTasks() {
        if (nameCacheTask != null) nameCacheTask.cancel();
        if (retentionTask != null) retentionTask.cancel();
        nameCacheTask = null;
        retentionTask = null;
    }

    private void refreshNameCache() {
        Database db = database;
        if (db == null || !isEnabled()) return;
        nameCache = Collections.unmodifiableList(db.getAllPlayerNames(NAME_CACHE_LIMIT));
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Database getDatabase()           { return database; }
    public LogService getLogService()       { return logService; }
    public GUIManager getGuiManager()       { return guiManager; }
    public List<String> getNameCache()      { return nameCache; }
}

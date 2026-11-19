package com.jestron.itemlog.database;

import com.jestron.itemlog.ItemLog;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite: tek kalıcı bağlantı + kilit.
 * Kilit, işlemin tamamı boyunca tutulur; böylece aynı bağlantı iki thread
 * tarafından aynı anda kullanılmaz ve bağlantı asla yanlışlıkla kapatılmaz.
 * SQLite JDBC sürücüsü tüm Spigot/Paper/Purpur sunucularında hazır gelir.
 */
public final class SQLiteDatabase extends Database {

    private final File file;
    private final ReentrantLock lock = new ReentrantLock(true);
    private Connection connection;

    public SQLiteDatabase(ItemLog plugin, String fileName) {
        super(plugin);
        this.file = new File(plugin.getDataFolder(), fileName == null || fileName.trim().isEmpty() ? "itemlog.db" : fileName);
    }

    @Override
    public void connect() throws Exception {
        Class.forName("org.sqlite.JDBC");
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Klasör oluşturulamadı: " + parent);
        }
        lock.lock();
        try {
            open();
        } finally {
            lock.unlock();
        }
        initSchema();
        plugin.getLogger().info("SQLite bağlandı: " + file.getName());
    }

    private void open() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA temp_store=MEMORY");
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
            lock.unlock();
        }
    }

    @Override
    public String getTypeName() {
        return "SQLite";
    }

    @Override
    protected boolean isMySQL() {
        return false;
    }

    @Override
    protected <T> T withConnection(SqlFunction<T> fn) throws SQLException {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) open();
            return fn.apply(connection);
        } finally {
            lock.unlock();
        }
    }

    /** sqlite-jdbc'nin tüm sürümlerinde çalışır (getGeneratedKeys yeni sürümlerde kaldırıldı). */
    @Override
    protected int lastInsertId(Connection c, PreparedStatement ps) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}

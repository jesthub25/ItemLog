package com.jestron.itemlog.database;

import com.jestron.itemlog.ItemLog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * MySQL / MariaDB: hafif bağlantı havuzu (Semaphore + boşta bekleyen kuyruk).
 * Bağlantılar ödünç alınırken doğrulanır, kopmuş olanlar yenisiyle değiştirilir;
 * ayrı bir keepalive görevine gerek yoktur.
 *
 * Sürücü sunucudan gelir: yeni sürümlerde com.mysql.cj.jdbc.Driver,
 * eski (1.16.x) sürümlerde com.mysql.jdbc.Driver.
 */
public final class MySQLDatabase extends Database {

    private final String url;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long timeoutMs;
    private final String display;

    private final ConcurrentLinkedQueue<Connection> idle = new ConcurrentLinkedQueue<>();
    private final Semaphore permits;
    private volatile boolean closed;

    public MySQLDatabase(ItemLog plugin, String host, int port, String database,
                         String username, String password, int poolSize, int timeoutMs, String properties) {
        super(plugin);
        String props = properties == null ? "" : properties.trim();
        if (props.startsWith("?")) props = props.substring(1);
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + (props.isEmpty() ? "" : "?" + props);
        this.username = username;
        this.password = password == null ? "" : password;
        this.poolSize = Math.max(1, Math.min(poolSize, 32));
        this.timeoutMs = Math.max(500, timeoutMs);
        this.permits = new Semaphore(this.poolSize, true);
        this.display = host + ":" + port + "/" + database;
    }

    @Override
    public void connect() throws Exception {
        loadDriver();
        DriverManager.setLoginTimeout((int) Math.max(1, timeoutMs / 1000));
        // İlk bağlantıyı hemen aç: kimlik/erişim hatası açılışta görünsün.
        idle.add(open());
        initSchema();
        plugin.getLogger().info("MySQL bağlandı: " + display + " (havuz=" + poolSize + ")");
    }

    private static void loadDriver() throws ClassNotFoundException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Class.forName("com.mysql.jdbc.Driver");
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public void close() {
        closed = true;
        Connection c;
        while ((c = idle.poll()) != null) quietClose(c);
    }

    @Override
    public String getTypeName() {
        return "MySQL";
    }

    @Override
    protected boolean isMySQL() {
        return true;
    }

    @Override
    protected <T> T withConnection(SqlFunction<T> fn) throws SQLException {
        if (closed) throw new SQLException("Veritabanı kapalı.");
        try {
            if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new SQLException("MySQL havuzu zaman aşımı (" + timeoutMs + " ms).");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Bağlantı beklerken kesildi.", e);
        }

        Connection c = null;
        try {
            c = idle.poll();
            if (c != null && !isUsable(c)) {
                quietClose(c);
                c = null;
            }
            if (c == null) c = open();
            T result = fn.apply(c);
            return result;
        } catch (SQLException ex) {
            if (c != null && !isUsable(c)) {
                quietClose(c);
                c = null;
            }
            throw ex;
        } finally {
            if (c != null) {
                if (closed) quietClose(c);
                else idle.offer(c);
            }
            permits.release();
        }
    }

    private static boolean isUsable(Connection c) {
        try {
            return !c.isClosed() && c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void quietClose(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    protected void createIndex(Connection c, String name, String columns) throws SQLException {
        // MySQL 5.7 "CREATE INDEX IF NOT EXISTS" desteklemez.
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.STATISTICS"
                + " WHERE table_schema=DATABASE() AND table_name=? AND index_name=?")) {
            ps.setString(1, TABLE);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE INDEX " + name + " ON " + TABLE + "(" + columns + ")");
        }
    }

    @Override
    protected int lastInsertId(Connection c, PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) return rs.getInt(1);
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}

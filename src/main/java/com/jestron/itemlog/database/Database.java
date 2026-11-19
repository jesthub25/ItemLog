package com.jestron.itemlog.database;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.model.LogEntry;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * SQLite ve MySQL için ortak SQL katmanı.
 *
 * Her işlem {@link #withConnection} üzerinden yürür; alt sınıf bağlantının
 * nasıl alınıp bırakılacağına (SQLite: kilit + tek bağlantı, MySQL: havuz)
 * karar verir. Hiçbir metod bağlantıyı kendisi kapatmaz.
 *
 * Tüm public metodlar thread-safe'dir ve ana thread'den ÇAĞRILMAMALIDIR.
 */
public abstract class Database {

    protected static final String TABLE = "item_logs";

    private static final String COLUMNS =
            "id,event_type,player_uuid,player_name,related_uuid,related_name,item_material,"
            + "item_amount,item_serialized,world,loc_x,loc_y,loc_z,timestamp,entity_uuid,linked_id";

    @FunctionalInterface
    protected interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    protected final ItemLog plugin;

    protected Database(ItemLog plugin) {
        this.plugin = plugin;
    }

    // ── Fabrika ────────────────────────────────────────────────────────────

    public static Database create(ItemLog plugin) {
        FileConfiguration c = plugin.getConfig();
        String type = c.getString("database.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (type.equals("mysql") || type.equals("mariadb")) {
            return new MySQLDatabase(plugin,
                    c.getString("database.mysql.host", "127.0.0.1"),
                    c.getInt("database.mysql.port", 3306),
                    c.getString("database.mysql.database", "itemlog"),
                    c.getString("database.mysql.username", "root"),
                    c.getString("database.mysql.password", ""),
                    c.getInt("database.mysql.pool-size", 5),
                    c.getInt("database.mysql.connection-timeout", 5000),
                    c.getString("database.mysql.properties",
                            "useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true"));
        }
        if (!type.equals("sqlite")) {
            plugin.getLogger().warning("Bilinmeyen database.type '" + type + "', SQLite kullanılıyor.");
        }
        return new SQLiteDatabase(plugin, c.getString("database.sqlite.file", "itemlog.db"));
    }

    // ── Alt sınıf sözleşmesi ───────────────────────────────────────────────

    public abstract void connect() throws Exception;

    public abstract void close();

    public abstract String getTypeName();

    protected abstract boolean isMySQL();

    protected abstract <T> T withConnection(SqlFunction<T> fn) throws SQLException;

    /** INSERT sonrası üretilen id'yi aynı bağlantı üzerinden döndürür. */
    protected abstract int lastInsertId(Connection c, PreparedStatement ps) throws SQLException;

    // ── Şema ───────────────────────────────────────────────────────────────

    protected final void initSchema() throws SQLException {
        final boolean my = isMySQL();
        final String ddl = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + (my ? "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," : "id INTEGER PRIMARY KEY AUTOINCREMENT,")
                + "event_type VARCHAR(20) NOT NULL,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(64) NOT NULL,"
                + "related_uuid VARCHAR(36),"
                + "related_name VARCHAR(64),"
                + "item_material VARCHAR(64) NOT NULL,"
                + "item_amount INT NOT NULL,"
                + "item_serialized " + (my ? "MEDIUMTEXT" : "TEXT") + ","
                + "world VARCHAR(64) NOT NULL,"
                + "loc_x DOUBLE NOT NULL,"
                + "loc_y DOUBLE NOT NULL,"
                + "loc_z DOUBLE NOT NULL,"
                + "timestamp BIGINT NOT NULL,"
                + "entity_uuid VARCHAR(36),"
                + "linked_id INT DEFAULT -1"
                + ")" + (my ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "");

        withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(ddl);
            }
            createIndex(c, "idx_il_player_ts",  "player_uuid, timestamp");
            createIndex(c, "idx_il_related_ts", "related_uuid, timestamp");
            createIndex(c, "idx_il_entity",     "entity_uuid");
            createIndex(c, "idx_il_ts",         "timestamp");
            createIndex(c, "idx_il_pname",      my ? "player_name"  : "player_name COLLATE NOCASE");
            createIndex(c, "idx_il_rname",      my ? "related_name" : "related_name COLLATE NOCASE");
            return null;
        });
    }

    protected void createIndex(Connection c, String name, String columns) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE INDEX IF NOT EXISTS " + name + " ON " + TABLE + "(" + columns + ")");
        }
    }

    // ── Yazma ──────────────────────────────────────────────────────────────

    /** @return üretilen id veya hata halinde -1 */
    public int insert(final LogEntry e) {
        final String sql = "INSERT INTO " + TABLE
                + " (event_type,player_uuid,player_name,related_uuid,related_name,item_material,item_amount,"
                + "item_serialized,world,loc_x,loc_y,loc_z,timestamp,entity_uuid,linked_id)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            return withConnection(c -> {
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, e.getEventType());
                    ps.setString(2, e.getPlayerUUID());
                    ps.setString(3, e.getPlayerName());
                    ps.setString(4, e.getRelatedUUID());
                    ps.setString(5, e.getRelatedName());
                    ps.setString(6, e.getItemMaterial());
                    ps.setInt(7, e.getItemAmount());
                    ps.setString(8, e.getItemSerialized());
                    ps.setString(9, e.getWorld());
                    ps.setDouble(10, e.getLocX());
                    ps.setDouble(11, e.getLocY());
                    ps.setDouble(12, e.getLocZ());
                    ps.setLong(13, e.getTimestamp());
                    ps.setString(14, e.getEntityUUID());
                    ps.setInt(15, e.getLinkedId());
                    ps.executeUpdate();
                    return lastInsertId(c, ps);
                }
            });
        } catch (SQLException ex) {
            error("insert", ex);
            return -1;
        }
    }

    /** Bir atma kaydını alan kişiyle ve alış kaydıyla ilişkilendirir. */
    public void linkDrop(final int dropId, final int pickupId, final String pickerUUID, final String pickerName) {
        update("linkDrop", "UPDATE " + TABLE + " SET linked_id=?, related_uuid=?, related_name=? WHERE id=?",
                pickupId, pickerUUID, pickerName, dropId);
    }

    /** Item birleşmesinde (ItemMergeEvent) bekleyen düşüş kayıtlarını hedef entity'ye taşır. */
    public void reassignEntity(final String oldEntity, final String newEntity) {
        update("reassignEntity", "UPDATE " + TABLE + " SET entity_uuid=? WHERE entity_uuid=? AND related_uuid IS NULL"
                + " AND event_type IN ('DROP','DEATH_DROP')", newEntity, oldEntity);
    }

    public int purgeBefore(final long timestamp) {
        try {
            return withConnection(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE timestamp < ?")) {
                    ps.setLong(1, timestamp);
                    return ps.executeUpdate();
                }
            });
        } catch (SQLException ex) {
            error("purgeBefore", ex);
            return 0;
        }
    }

    // ── Okuma ──────────────────────────────────────────────────────────────

    /** Bu entity için henüz kimsenin almadığı DROP / DEATH_DROP kayıtları (eskiden yeniye). */
    public List<LogEntry> findPendingDrops(String entityUUID) {
        if (entityUUID == null) return Collections.emptyList();
        return queryList("findPendingDrops", "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE entity_uuid=? AND related_uuid IS NULL AND event_type IN ('DROP','DEATH_DROP')"
                + " ORDER BY id ASC LIMIT 32", entityUUID);
    }

    public LogEntry getById(int id) {
        List<LogEntry> list = queryList("getById",
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id=?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<LogEntry> getPlayerLogs(String uuid, List<String> types, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        params.add(uuid);
        params.add(uuid);
        String filter = typeFilter(types, params);
        params.add(limit);
        params.add(offset);
        return queryList("getPlayerLogs", "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE (player_uuid=? OR related_uuid=?)" + filter
                + " ORDER BY timestamp DESC, id DESC LIMIT ? OFFSET ?", params.toArray());
    }

    public int countPlayerLogs(String uuid, List<String> types) {
        List<Object> params = new ArrayList<>();
        params.add(uuid);
        params.add(uuid);
        String filter = typeFilter(types, params);
        return queryInt("countPlayerLogs", "SELECT COUNT(*) FROM " + TABLE
                + " WHERE (player_uuid=? OR related_uuid=?)" + filter, params.toArray());
    }

    public List<LogEntry> getTradeLogs(String a, String b, int limit, int offset) {
        return queryList("getTradeLogs", "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE event_type IN ('DROP','DEATH_DROP')"
                + " AND ((player_uuid=? AND related_uuid=?) OR (player_uuid=? AND related_uuid=?))"
                + " ORDER BY timestamp DESC, id DESC LIMIT ? OFFSET ?", a, b, b, a, limit, offset);
    }

    public int countTradeLogs(String a, String b) {
        return queryInt("countTradeLogs", "SELECT COUNT(*) FROM " + TABLE
                + " WHERE event_type IN ('DROP','DEATH_DROP')"
                + " AND ((player_uuid=? AND related_uuid=?) OR (player_uuid=? AND related_uuid=?))", a, b, b, a);
    }

    /**
     * İsimden UUID bulur (büyük/küçük harf duyarsız, en son kayıt).
     * Mojang'a istek atmaz; offline/cracked sunucularda da doğru UUID'yi verir.
     *
     * @return {uuid, gerçekİsim} veya null
     */
    public String[] findPlayer(final String name) {
        final String nocase = isMySQL() ? "" : " COLLATE NOCASE";
        try {
            return withConnection(c -> {
                String[] r = findIn(c, "SELECT player_uuid, player_name FROM " + TABLE
                        + " WHERE player_name=?" + nocase + " ORDER BY id DESC LIMIT 1", name);
                if (r != null) return r;
                return findIn(c, "SELECT related_uuid, related_name FROM " + TABLE
                        + " WHERE related_name=?" + nocase + " AND related_uuid IS NOT NULL ORDER BY id DESC LIMIT 1", name);
            });
        } catch (SQLException ex) {
            error("findPlayer", ex);
            return null;
        }
    }

    private static String[] findIn(Connection c, String sql, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) return new String[]{rs.getString(1), rs.getString(2)};
            }
        }
        return null;
    }

    /** Tab tamamlama için loglarda geçen tüm oyuncu adları. */
    public List<String> getAllPlayerNames(final int max) {
        final String sql = "SELECT player_name FROM " + TABLE + " GROUP BY player_name"
                + " UNION SELECT related_name FROM " + TABLE + " WHERE related_name IS NOT NULL GROUP BY related_name";
        try {
            return withConnection(c -> {
                java.util.TreeSet<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next() && out.size() < max) {
                        String n = rs.getString(1);
                        if (n != null && !n.trim().isEmpty()) out.add(n);
                    }
                }
                return new ArrayList<>(out);
            });
        } catch (SQLException ex) {
            error("getAllPlayerNames", ex);
            return Collections.emptyList();
        }
    }

    // ── Yardımcılar ────────────────────────────────────────────────────────

    private static String typeFilter(List<String> types, List<Object> params) {
        if (types == null || types.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" AND event_type IN (");
        for (int i = 0; i < types.size(); i++) {
            sb.append(i == 0 ? "?" : ",?");
            params.add(types.get(i));
        }
        return sb.append(')').toString();
    }

    private void update(String op, final String sql, final Object... params) {
        try {
            withConnection(c -> {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    bind(ps, params);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLException ex) {
            error(op, ex);
        }
    }

    private List<LogEntry> queryList(String op, final String sql, final Object... params) {
        try {
            return withConnection(c -> {
                List<LogEntry> out = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    bind(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) out.add(map(rs));
                    }
                }
                return out;
            });
        } catch (SQLException ex) {
            error(op, ex);
            return Collections.emptyList();
        }
    }

    private int queryInt(String op, final String sql, final Object... params) {
        try {
            return withConnection(c -> {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    bind(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getInt(1) : 0;
                    }
                }
            });
        } catch (SQLException ex) {
            error(op, ex);
            return 0;
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
            else if (p instanceof Long) ps.setLong(i + 1, (Long) p);
            else ps.setString(i + 1, p == null ? null : String.valueOf(p));
        }
    }

    private static LogEntry map(ResultSet rs) throws SQLException {
        LogEntry e = new LogEntry();
        e.setId(rs.getInt("id"));
        e.setEventType(rs.getString("event_type"));
        e.setPlayerUUID(rs.getString("player_uuid"));
        e.setPlayerName(rs.getString("player_name"));
        e.setRelatedUUID(rs.getString("related_uuid"));
        e.setRelatedName(rs.getString("related_name"));
        e.setItemMaterial(rs.getString("item_material"));
        e.setItemAmount(rs.getInt("item_amount"));
        e.setItemSerialized(rs.getString("item_serialized"));
        e.setWorld(rs.getString("world"));
        e.setLocX(rs.getDouble("loc_x"));
        e.setLocY(rs.getDouble("loc_y"));
        e.setLocZ(rs.getDouble("loc_z"));
        e.setTimestamp(rs.getLong("timestamp"));
        e.setEntityUUID(rs.getString("entity_uuid"));
        e.setLinkedId(rs.getInt("linked_id"));
        return e;
    }

    protected void error(String op, Exception ex) {
        plugin.getLogger().log(Level.WARNING, "Veritabanı hatası (" + op + "): " + ex.getMessage());
    }
}

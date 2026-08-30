package fr.skynex.worldx.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.skynex.worldx.WorldX;
import org.bukkit.Bukkit;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.edit.Palette;
import fr.skynex.worldx.edit.EditOperation;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DatabaseManager {

    private final WorldX plugin;
    private final ExecutorService dbExecutor;
    private HikariDataSource dataSource;
    private String sqliteUrl;
    private boolean isMySQL;

    public DatabaseManager(WorldX plugin) {
        this.plugin = plugin;
        // Single thread executor to serialize database queries (safe SQLite execution)
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WorldX-Database-Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() {
        String type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        isMySQL = type.equals("mysql");

        if (isMySQL) {
            setupMySQL();
        } else {
            setupSQLite();
        }

        createTables();
    }

    private void setupMySQL() {
        plugin.getLogger().info("Connecting to MySQL database...");
        try {
            HikariConfig config = new HikariConfig();
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "worldx");
            String user = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "");

            config.setJdbcUrl(
                    "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&characterEncoding=utf-8");
            config.setUsername(user);
            config.setPassword(password);

            config.setMaximumPoolSize(plugin.getConfig().getInt("database.mysql.pool.maximum-pool-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.mysql.pool.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.mysql.pool.connection-timeout", 30000));
            config.setMaxLifetime(plugin.getConfig().getLong("database.mysql.pool.max-lifetime", 1800000));

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("MySQL connection established successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL database! Falling back to SQLite...", e);
            isMySQL = false;
            setupSQLite();
        }
    }

    private void setupSQLite() {
        plugin.getLogger().info("Connecting to local SQLite database...");
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String fileName = plugin.getConfig().getString("database.sqlite.file-name", "regions.db");
        File dbFile = new File(dataFolder, fileName);
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create SQLite database file", e);
            }
        }

        sqliteUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(sqliteUrl)) {
                plugin.getLogger().info("SQLite connection test successful.");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC Driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite Connection Error!", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (isMySQL) {
            if (dataSource == null) {
                throw new SQLException("HikariDataSource is not initialized!");
            }
            return dataSource.getConnection();
        } else {
            if (sqliteUrl == null) {
                setupSQLite();
            }
            return DriverManager.getConnection(sqliteUrl);
        }
    }

    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close database connection", e);
            }
        }
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
        dbExecutor.shutdown();
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, dbExecutor);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, dbExecutor);
    }

    private void createTables() {
        runAsync(() -> {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Table to store regions
                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_regions (" +
                        "id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "min_x INT NOT NULL, " +
                        "min_y INT NOT NULL, " +
                        "min_z INT NOT NULL, " +
                        "max_x INT NOT NULL, " +
                        "max_y INT NOT NULL, " +
                        "max_z INT NOT NULL, " +
                        "priority INT NOT NULL DEFAULT 0, " +
                        "parent_id VARCHAR(64), " +
                        "owners TEXT, " +
                        "members TEXT, " +
                        "flags TEXT, " +
                        "shape_type VARCHAR(20) NOT NULL DEFAULT 'CUBOID', " +
                        "poly_points TEXT, " +
                        "bound_schematic VARCHAR(64)" +
                        ")");
                // Migration to add shape_type column to existing tables
                try {
                    stmt.execute(
                            "ALTER TABLE worldx_regions ADD COLUMN shape_type VARCHAR(20) NOT NULL DEFAULT 'CUBOID'");
                } catch (SQLException ignored) {
                    // Column already exists, ignore
                }
                try {
                    stmt.execute("ALTER TABLE worldx_regions ADD COLUMN poly_points TEXT DEFAULT NULL");
                } catch (SQLException ignored) {
                }
                try {
                    stmt.execute("ALTER TABLE worldx_regions ADD COLUMN bound_schematic VARCHAR(64) DEFAULT NULL");
                } catch (SQLException ignored) {
                }

                // Table to store schematics
                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_schematics (" +
                        "name VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "data MEDIUMTEXT NOT NULL, " +
                        "version INT NOT NULL DEFAULT 1" +
                        ")");
                try {
                    stmt.execute("ALTER TABLE worldx_schematics ADD COLUMN version INT NOT NULL DEFAULT 1");
                } catch (SQLException ignored) {
                }

                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_schematic_history (" +
                        "name VARCHAR(64) NOT NULL, " +
                        "version INT NOT NULL, " +
                        "data MEDIUMTEXT NOT NULL, " +
                        "created_at BIGINT NOT NULL, " +
                        "PRIMARY KEY (name, version)" +
                        ")");

                // Table to store rollback logs
                String logsTable = "CREATE TABLE IF NOT EXISTS worldx_rollback_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "region_id VARCHAR(64) NOT NULL, " +
                        "world VARCHAR(64) NOT NULL, " +
                        "x INT NOT NULL, " +
                        "y INT NOT NULL, " +
                        "z INT NOT NULL, " +
                        "previous_block TEXT NOT NULL, " +
                        "new_block TEXT NOT NULL, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "timestamp BIGINT NOT NULL" +
                        ")";
                if (isMySQL) {
                    logsTable = "CREATE TABLE IF NOT EXISTS worldx_rollback_logs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "region_id VARCHAR(64) NOT NULL, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x INT NOT NULL, " +
                            "y INT NOT NULL, " +
                            "z INT NOT NULL, " +
                            "previous_block TEXT NOT NULL, " +
                            "new_block TEXT NOT NULL, " +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "timestamp BIGINT NOT NULL" +
                            ")";
                }
                stmt.execute(logsTable);

                // Table to store player history
                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_player_history (" +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "history_type VARCHAR(10) NOT NULL, " +
                        "sequence_id INT NOT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "data MEDIUMTEXT NOT NULL, " +
                        "PRIMARY KEY (player_uuid, history_type, sequence_id)" +
                        ")");

                // Table to store palettes
                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_palettes (" +
                        "name VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "data TEXT NOT NULL" +
                        ")");

                // Table to store shared schematics
                stmt.execute("CREATE TABLE IF NOT EXISTS worldx_shared_schematics (" +
                        "share_code VARCHAR(10) NOT NULL PRIMARY KEY, " +
                        "data MEDIUMTEXT NOT NULL, " +
                        "created_at BIGINT NOT NULL" +
                        ")");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create database tables!", e);
            }
        });
    }

    // region Database Access Methods

    public List<Region> loadAllRegions() throws SQLException {
        List<Region> regions = new ArrayList<>();
        String sql = "SELECT * FROM worldx_regions";

        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String worldName = rs.getString("world_name");
                int minX = rs.getInt("min_x");
                int minY = rs.getInt("min_y");
                int minZ = rs.getInt("min_z");
                int maxX = rs.getInt("max_x");
                int maxY = rs.getInt("max_y");
                int maxZ = rs.getInt("max_z");
                int priority = rs.getInt("priority");
                String parentId = rs.getString("parent_id");
                String ownersStr = rs.getString("owners");
                String membersStr = rs.getString("members");
                String flagsStr = rs.getString("flags");
                String shapeTypeStr = rs.getString("shape_type");
                String polyPointsStr = rs.getString("poly_points");
                String boundSchemStr = rs.getString("bound_schematic");

                // Parse owners/members
                List<UUID> owners = parseUUIDList(ownersStr);
                List<UUID> members = parseUUIDList(membersStr);

                // Parse flags
                Map<String, String> flags = parseFlags(flagsStr);
                fr.skynex.worldx.region.ShapeType shapeType = fr.skynex.worldx.region.ShapeType
                        .valueOf(shapeTypeStr.toUpperCase());

                Region region = new Region(id, worldName, minX, minY, minZ, maxX, maxY, maxZ, shapeType);
                region.setPriority(priority);
                region.setParentId(parentId);
                owners.forEach(region::addOwner);
                members.forEach(region::addMember);
                region.getFlags().putAll(flags);
                region.setBoundSchematic(boundSchemStr);

                // Parse poly points
                List<int[]> polyPoints = new ArrayList<>();
                if (polyPointsStr != null && !polyPointsStr.isEmpty()) {
                    for (String pair : polyPointsStr.split(";")) {
                        String[] parts = pair.split(",");
                        if (parts.length == 2) {
                            try {
                                polyPoints.add(new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) });
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
                region.setPolyPoints(polyPoints);

                regions.add(region);
            }
        }
        return regions;
    }

    public CompletableFuture<Region> loadRegion(String id) {
        return supplyAsync(() -> {
            String sql = "SELECT * FROM worldx_regions WHERE id = ?";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("world_name");
                        int minX = rs.getInt("min_x");
                        int minY = rs.getInt("min_y");
                        int minZ = rs.getInt("min_z");
                        int maxX = rs.getInt("max_x");
                        int maxY = rs.getInt("max_y");
                        int maxZ = rs.getInt("max_z");
                        int priority = rs.getInt("priority");
                        String parentId = rs.getString("parent_id");
                        String ownersStr = rs.getString("owners");
                        String membersStr = rs.getString("members");
                        String flagsStr = rs.getString("flags");
                        String shapeTypeStr = rs.getString("shape_type");
                        String polyPointsStr = rs.getString("poly_points");
                        String boundSchemStr = rs.getString("bound_schematic");

                        // Parse owners/members
                        List<UUID> owners = parseUUIDList(ownersStr);
                        List<UUID> members = parseUUIDList(membersStr);

                        // Parse flags
                        Map<String, String> flags = parseFlags(flagsStr);
                        fr.skynex.worldx.region.ShapeType shapeType = fr.skynex.worldx.region.ShapeType
                                .valueOf(shapeTypeStr.toUpperCase());

                        Region region = new Region(id, worldName, minX, minY, minZ, maxX, maxY, maxZ, shapeType);
                        region.setPriority(priority);
                        region.setParentId(parentId);
                        owners.forEach(region::addOwner);
                        members.forEach(region::addMember);
                        region.getFlags().putAll(flags);
                        region.setBoundSchematic(boundSchemStr);

                        // Parse poly points
                        List<int[]> polyPoints = new ArrayList<>();
                        if (polyPointsStr != null && !polyPointsStr.isEmpty()) {
                            for (String pair : polyPointsStr.split(";")) {
                                String[] parts = pair.split(",");
                                if (parts.length == 2) {
                                    try {
                                        polyPoints.add(
                                                new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) });
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }
                        }
                        region.setPolyPoints(polyPoints);

                        return region;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load region: " + id, e);
            }
            return null;
        });
    }

    public void saveRegion(Region region) {
        runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO worldx_regions (id, world_name, min_x, min_y, min_z, max_x, max_y, max_z, priority, parent_id, owners, members, flags, shape_type, poly_points, bound_schematic) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            if (isMySQL) {
                sql = "INSERT INTO worldx_regions (id, world_name, min_x, min_y, min_z, max_x, max_y, max_z, priority, parent_id, owners, members, flags, shape_type, poly_points, bound_schematic) "
                        +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE world_name=VALUES(world_name), min_x=VALUES(min_x), min_y=VALUES(min_y), min_z=VALUES(min_z), "
                        +
                        "max_x=VALUES(max_x), max_y=VALUES(max_y), max_z=VALUES(max_z), priority=VALUES(priority), parent_id=VALUES(parent_id), "
                        +
                        "owners=VALUES(owners), members=VALUES(members), flags=VALUES(flags), shape_type=VALUES(shape_type), poly_points=VALUES(poly_points), bound_schematic=VALUES(bound_schematic)";
            }

            // Serialize poly points
            StringBuilder sb = new StringBuilder();
            for (int[] p : region.getPolyPoints()) {
                if (sb.length() > 0)
                    sb.append(";");
                sb.append(p[0]).append(",").append(p[1]);
            }

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, region.getId());
                ps.setString(2, region.getWorldName());
                ps.setInt(3, region.getMinX());
                ps.setInt(4, region.getMinY());
                ps.setInt(5, region.getMinZ());
                ps.setInt(6, region.getMaxX());
                ps.setInt(7, region.getMaxY());
                ps.setInt(8, region.getMaxZ());
                ps.setInt(9, region.getPriority());
                ps.setString(10, region.getParentId());
                ps.setString(11, serializeUUIDList(region.getOwners()));
                ps.setString(12, serializeUUIDList(region.getMembers()));
                ps.setString(13, serializeFlags(region.getFlags()));
                ps.setString(14, region.getShapeType().name());
                ps.setString(15, sb.length() > 0 ? sb.toString() : null);
                ps.setString(16, region.getBoundSchematic());

                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save region " + region.getId() + " to database!", e);
            }
        });
    }

    public void deleteRegion(String regionId) {
        runAsync(() -> {
            String sql = "DELETE FROM worldx_regions WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, regionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete region " + regionId + " from database!", e);
            }
        });
    }

    public void saveSchematic(String name, byte[] data) {
        runAsync(() -> {
            String base64 = java.util.Base64.getEncoder().encodeToString(data);

            int version = 0;
            String currentData = null;
            String selectSql = "SELECT version, data FROM worldx_schematics WHERE name = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        version = rs.getInt("version");
                        currentData = rs.getString("data");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to read current version of schematic: " + name, e);
            }

            try (Connection conn = getConnection()) {
                if (version > 0 && currentData != null) {
                    String archiveSql = "INSERT OR REPLACE INTO worldx_schematic_history (name, version, data, created_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(archiveSql)) {
                        ps.setString(1, name);
                        ps.setInt(2, version);
                        ps.setString(3, currentData);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                }

                String insertSql = "INSERT OR REPLACE INTO worldx_schematics (name, data, version) VALUES (?, ?, ?)";
                if (isMySQL) {
                    insertSql = "INSERT INTO worldx_schematics (name, data, version) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE data=VALUES(data), version=VALUES(version)";
                }
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, name);
                    ps.setString(2, base64);
                    ps.setInt(3, version + 1);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save schematic version: " + name, e);
            }
        });
    }

    public CompletableFuture<byte[]> loadSchematic(String name) {
        return supplyAsync(() -> {
            String sql = "SELECT data FROM worldx_schematics WHERE name = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("data");
                        return java.util.Base64.getDecoder().decode(base64);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load schematic \"" + name + "\" from database!", e);
            }
            return null;
        });
    }

    public void logBlockChange(String regionId, String world, int x, int y, int z, String oldBlock, String newBlock,
            UUID player) {
        runAsync(() -> {
            String sql = "INSERT INTO worldx_rollback_logs (region_id, world, x, y, z, previous_block, new_block, player_uuid, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, regionId);
                ps.setString(2, world);
                ps.setInt(3, x);
                ps.setInt(4, y);
                ps.setInt(5, z);
                ps.setString(6, oldBlock);
                ps.setString(7, newBlock);
                ps.setString(8, player != null ? player.toString() : "SYSTEM");
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to write rollback log!", e);
            }
        });
    }

    public CompletableFuture<List<RollbackEntry>> getRollbackLogs(String regionId, UUID player, long maxAgeMs) {
        return supplyAsync(() -> {
            List<RollbackEntry> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder("SELECT * FROM worldx_rollback_logs WHERE region_id = ?");
            if (player != null) {
                sb.append(" AND player_uuid = ?");
            }
            if (maxAgeMs > 0) {
                sb.append(" AND timestamp >= ?");
            }
            sb.append(" ORDER BY id DESC");

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setString(idx++, regionId);
                if (player != null) {
                    ps.setString(idx++, player.toString());
                }
                if (maxAgeMs > 0) {
                    ps.setLong(idx++, System.currentTimeMillis() - maxAgeMs);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new RollbackEntry(
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("previous_block"),
                                rs.getString("new_block"),
                                rs.getString("player_uuid"),
                                rs.getLong("timestamp")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query rollback logs!", e);
            }
            return list;
        });
    }

    public void savePalette(Palette palette) {
        runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO worldx_palettes (name, data) VALUES (?, ?)";
            if (isMySQL) {
                sql = "INSERT INTO worldx_palettes (name, data) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE data=VALUES(data)";
            }

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, palette.getName());
                ps.setString(2, palette.serialize());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save palette \"" + palette.getName() + "\" to database!", e);
            }
        });
    }

    public CompletableFuture<Palette> loadPalette(String name) {
        return supplyAsync(() -> {
            String sql = "SELECT data FROM worldx_palettes WHERE name = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("data");
                        return Palette.deserialize(name, json);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load palette \"" + name + "\" from database!", e);
            }
            return null;
        });
    }

    public CompletableFuture<List<String>> getPaletteNames() {
        return supplyAsync(() -> {
            List<String> list = new ArrayList<>();
            String sql = "SELECT name FROM worldx_palettes";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query palette names from database!", e);
            }
            return list;
        });
    }

    public void shareSchematic(String code, byte[] data) {
        runAsync(() -> {
            String base64 = java.util.Base64.getEncoder().encodeToString(data);
            String sql = "INSERT OR REPLACE INTO worldx_shared_schematics (share_code, data, created_at) VALUES (?, ?, ?)";
            if (isMySQL) {
                sql = "INSERT INTO worldx_shared_schematics (share_code, data, created_at) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE data=VALUES(data), created_at=VALUES(created_at)";
            }

            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setString(2, base64);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to share schematic with code \"" + code + "\"!", e);
            }
        });
    }

    public CompletableFuture<byte[]> importSchematic(String code) {
        return supplyAsync(() -> {
            String sql = "SELECT data FROM worldx_shared_schematics WHERE share_code = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("data");
                        return java.util.Base64.getDecoder().decode(base64);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to import shared schematic \"" + code + "\"!", e);
            }
            return null;
        });
    }

    // endregion

    // region Helpers

    private List<UUID> parseUUIDList(String str) {
        List<UUID> list = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return list;
        }
        for (String s : str.split(",")) {
            try {
                list.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return list;
    }

    private String serializeUUIDList(List<UUID> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : list) {
            sb.append(uuid.toString()).append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private Map<String, String> parseFlags(String str) {
        Map<String, String> map = new HashMap<>();
        if (str == null || str.trim().isEmpty()) {
            return map;
        }
        for (String pair : str.split(";")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private String serializeFlags(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(";");
        }
        return sb.toString();
    }

    public int getActiveConnectionsCount() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                return dataSource.getHikariPoolMXBean().getActiveConnections();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    // region Schematic history versioning
    public CompletableFuture<List<SchematicVersion>> getSchematicHistory(String name) {
        return supplyAsync(() -> {
            List<SchematicVersion> history = new ArrayList<>();
            String sql = "SELECT version, created_at FROM worldx_schematic_history WHERE name = ? ORDER BY version DESC";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(new SchematicVersion(rs.getInt("version"), rs.getLong("created_at")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load schematic history: " + name, e);
            }
            return history;
        });
    }

    public CompletableFuture<byte[]> loadSchematicVersion(String name, int version) {
        return supplyAsync(() -> {
            String sql = "SELECT data FROM worldx_schematic_history WHERE name = ? AND version = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setInt(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("data");
                        return java.util.Base64.getDecoder().decode(base64);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load archived schematic version: " + name + " v" + version, e);
            }
            return null;
        });
    }

    public static class SchematicVersion {
        private final int version;
        private final long timestamp;

        public SchematicVersion(int version, long timestamp) {
            this.version = version;
            this.timestamp = timestamp;
        }

        public int getVersion() {
            return version;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
    // endregion

    private String serializeEditOperation(EditOperation op) {
        StringBuilder sb = new StringBuilder();
        for (EditOperation.BlockChange bc : op.getChanges()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(bc.getX()).append(":")
              .append(bc.getY()).append(":")
              .append(bc.getZ()).append(":")
              .append(bc.getPreviousData().getAsString()).append(":")
              .append(bc.getNewData().getAsString());
        }
        return sb.toString();
    }

    private EditOperation deserializeEditOperation(String worldName, String data) {
        List<EditOperation.BlockChange> changes = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) {
            return new EditOperation(worldName, changes);
        }
        for (String part : data.split(";")) {
            String[] split = part.split(":", 5);
            if (split.length == 5) {
                try {
                    int x = Integer.parseInt(split[0]);
                    int y = Integer.parseInt(split[1]);
                    int z = Integer.parseInt(split[2]);
                    BlockData prev = Bukkit.createBlockData(split[3]);
                    BlockData curr = Bukkit.createBlockData(split[4]);
                    changes.add(new EditOperation.BlockChange(x, y, z, prev, curr));
                } catch (Exception ignored) {}
            }
        }
        return new EditOperation(worldName, changes);
    }

    public void savePlayerHistory(UUID playerUUID, String type, List<EditOperation> operations) {
        runAsync(() -> {
            String deleteSql = "DELETE FROM worldx_player_history WHERE player_uuid = ? AND history_type = ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, type);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete player history", e);
            }

            String insertSql = "INSERT INTO worldx_player_history (player_uuid, history_type, sequence_id, world_name, data) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql)) {
                conn.setAutoCommit(false);
                int seq = 0;
                for (EditOperation op : operations) {
                    ps.setString(1, playerUUID.toString());
                    ps.setString(2, type);
                    ps.setInt(3, seq++);
                    ps.setString(4, op.getWorldName());
                    ps.setString(5, serializeEditOperation(op));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player history", e);
            }
        });
    }

    public CompletableFuture<List<EditOperation>> loadPlayerHistory(UUID playerUUID, String type) {
        return supplyAsync(() -> {
            List<EditOperation> list = new ArrayList<>();
            String sql = "SELECT world_name, data FROM worldx_player_history WHERE player_uuid = ? AND history_type = ? ORDER BY sequence_id ASC";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, type);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String worldName = rs.getString("world_name");
                        String data = rs.getString("data");
                        list.add(deserializeEditOperation(worldName, data));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player history", e);
            }
            return list;
        });
    }
    // endregion

    // endregion
}

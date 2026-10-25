package dev.oakheart.oakhunt.storage;

import dev.oakheart.oakhunt.model.Head;
import dev.oakheart.oakhunt.model.Hunt;
import dev.oakheart.oakhunt.model.Milestone;
import dev.oakheart.oakhunt.model.PlayerResult;
import dev.oakheart.oakhunt.model.Reward;
import dev.oakheart.oakhunt.model.RewardType;
import dev.oakheart.oakhunt.model.Run;
import dev.oakheart.oakhunt.model.Skin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite persistence. Everything is loaded into memory at startup; afterwards the plugin writes
 * through on a single background thread, so writes keep their order and never block the server.
 *
 * <p>Ids are assigned in memory ({@link #nextId}) so inserts can run asynchronously.
 */
public class HuntDatabase {

    private final File file;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OakHunt-DB");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Integer> sequences = new HashMap<>();
    private Connection connection;

    public HuntDatabase(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public synchronized void open() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=OFF");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS hunts (
                      id TEXT PRIMARY KEY, display_name TEXT NOT NULL, description TEXT,
                      active INTEGER NOT NULL DEFAULT 0, start_at INTEGER, end_at INTEGER,
                      start_continues INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS hunt_settings (
                      hunt_id TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL,
                      PRIMARY KEY (hunt_id, key))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS skins (
                      id INTEGER PRIMARY KEY, hunt_id TEXT NOT NULL, texture TEXT NOT NULL, label TEXT,
                      archived INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS heads (
                      id INTEGER PRIMARY KEY, hunt_id TEXT NOT NULL, world TEXT NOT NULL,
                      x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                      block_data TEXT NOT NULL, texture TEXT NOT NULL, number INTEGER NOT NULL, clue TEXT,
                      archived INTEGER NOT NULL DEFAULT 0, conflict INTEGER NOT NULL DEFAULT 0,
                      created_by TEXT, created_at INTEGER NOT NULL)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS runs (
                      id INTEGER PRIMARY KEY, hunt_id TEXT NOT NULL, number INTEGER NOT NULL,
                      started_at INTEGER NOT NULL, ended_at INTEGER, imported INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS finds (
                      run_id INTEGER NOT NULL, head_id INTEGER NOT NULL, uuid TEXT NOT NULL,
                      found_at INTEGER NOT NULL, PRIMARY KEY (run_id, head_id, uuid))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS results (
                      run_id INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL,
                      found INTEGER NOT NULL, last_found_at INTEGER NOT NULL, completed_at INTEGER,
                      PRIMARY KEY (run_id, uuid))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS milestones (
                      id INTEGER PRIMARY KEY, hunt_id TEXT NOT NULL, threshold INTEGER NOT NULL,
                      message TEXT, broadcast TEXT, archived INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS rewards (
                      id INTEGER PRIMARY KEY, milestone_id INTEGER NOT NULL, type TEXT NOT NULL,
                      value TEXT, amount INTEGER NOT NULL DEFAULT 1, item BLOB,
                      archived INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS payouts (
                      run_id INTEGER NOT NULL, milestone_id INTEGER NOT NULL, uuid TEXT NOT NULL,
                      paid_at INTEGER NOT NULL, PRIMARY KEY (run_id, milestone_id, uuid))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS audit (
                      id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, actor TEXT,
                      hunt_id TEXT, action TEXT NOT NULL, detail TEXT)""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_heads_hunt ON heads(hunt_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_finds_run ON finds(run_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_results_run ON results(run_id)");
        }
        for (String table : List.of("skins", "heads", "runs", "milestones", "rewards")) {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
                sequences.put(table, rs.getInt(1));
            }
        }
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for pending database writes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to close database", e);
            }
        }
    }

    /** Next id for an in-memory insert into {@code table}. Main thread only. */
    public int nextId(String table) {
        return sequences.merge(table, 1, Integer::sum);
    }

    // ---------------------------------------------------------------- loading

    public synchronized List<Hunt> loadHunts() throws SQLException {
        List<Hunt> hunts = new ArrayList<>();
        Map<String, Hunt> byId = new HashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM hunts")) {
            while (rs.next()) {
                Hunt hunt = new Hunt(rs.getString("id"), rs.getString("display_name"), rs.getString("description"),
                        rs.getInt("active") == 1, longOrNull(rs, "start_at"), longOrNull(rs, "end_at"),
                        rs.getInt("start_continues") == 1, rs.getInt("archived") == 1, rs.getLong("created_at"));
                hunts.add(hunt);
                byId.put(hunt.getId(), hunt);
            }
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM hunt_settings")) {
            while (rs.next()) {
                Hunt hunt = byId.get(rs.getString("hunt_id"));
                if (hunt != null) hunt.getSettings().put(rs.getString("key"), rs.getString("value"));
            }
        }
        return hunts;
    }

    public synchronized List<Skin> loadSkins() throws SQLException {
        List<Skin> skins = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM skins ORDER BY id")) {
            while (rs.next()) {
                skins.add(new Skin(rs.getInt("id"), rs.getString("hunt_id"), rs.getString("texture"),
                        rs.getString("label"), rs.getInt("archived") == 1));
            }
        }
        return skins;
    }

    public synchronized List<Head> loadHeads() throws SQLException {
        List<Head> heads = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM heads ORDER BY id")) {
            while (rs.next()) {
                heads.add(new Head(rs.getInt("id"), rs.getString("hunt_id"), rs.getString("world"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getString("block_data"),
                        rs.getString("texture"), rs.getInt("number"), rs.getString("clue"),
                        rs.getInt("archived") == 1, rs.getInt("conflict") == 1,
                        rs.getString("created_by"), rs.getLong("created_at")));
            }
        }
        return heads;
    }

    public synchronized List<Run> loadRuns() throws SQLException {
        List<Run> runs = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM runs ORDER BY id")) {
            while (rs.next()) {
                runs.add(new Run(rs.getInt("id"), rs.getString("hunt_id"), rs.getInt("number"),
                        rs.getLong("started_at"), longOrNull(rs, "ended_at"), rs.getInt("imported") == 1));
            }
        }
        return runs;
    }

    public synchronized List<Milestone> loadMilestones() throws SQLException {
        Map<Integer, Milestone> byId = new HashMap<>();
        List<Milestone> milestones = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM milestones WHERE archived = 0 ORDER BY id")) {
            while (rs.next()) {
                Milestone milestone = new Milestone(rs.getInt("id"), rs.getString("hunt_id"),
                        rs.getInt("threshold"), rs.getString("message"), rs.getString("broadcast"));
                milestones.add(milestone);
                byId.put(milestone.getId(), milestone);
            }
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rewards WHERE archived = 0 ORDER BY id")) {
            while (rs.next()) {
                Milestone milestone = byId.get(rs.getInt("milestone_id"));
                if (milestone == null) continue;
                RewardType type;
                try {
                    type = RewardType.valueOf(rs.getString("type"));
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping reward " + rs.getInt("id") + " with unknown type " + rs.getString("type"));
                    continue;
                }
                milestone.getRewards().add(new Reward(rs.getInt("id"), milestone.getId(), type,
                        rs.getString("value"), rs.getInt("amount"), rs.getBytes("item")));
            }
        }
        return milestones;
    }

    public synchronized List<PlayerResult> loadResults() throws SQLException {
        List<PlayerResult> results = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM results")) {
            while (rs.next()) {
                results.add(new PlayerResult(rs.getInt("run_id"), UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"), rs.getInt("found"), rs.getLong("last_found_at"),
                        longOrNull(rs, "completed_at")));
            }
        }
        return results;
    }

    /** Finds for one run: player to the ids of heads they found. */
    public synchronized Map<UUID, Set<Integer>> loadFinds(int runId) throws SQLException {
        Map<UUID, Set<Integer>> finds = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT head_id, uuid FROM finds WHERE run_id = ?")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    finds.computeIfAbsent(UUID.fromString(rs.getString("uuid")), k -> new HashSet<>())
                            .add(rs.getInt("head_id"));
                }
            }
        }
        return finds;
    }

    /** Payouts for one run: player to the milestone ids already paid. */
    public synchronized Map<UUID, Set<Integer>> loadPayouts(int runId) throws SQLException {
        Map<UUID, Set<Integer>> payouts = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT milestone_id, uuid FROM payouts WHERE run_id = ?")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.computeIfAbsent(UUID.fromString(rs.getString("uuid")), k -> new HashSet<>())
                            .add(rs.getInt("milestone_id"));
                }
            }
        }
        return payouts;
    }

    /** How many players found each head in a run. */
    public synchronized Map<Integer, Integer> loadFindCounts(int runId) throws SQLException {
        Map<Integer, Integer> counts = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT head_id, COUNT(*) FROM finds WHERE run_id = ? GROUP BY head_id")) {
            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) counts.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return counts;
    }

    // ---------------------------------------------------------------- writes (async)

    public void saveHunt(Hunt hunt) {
        String id = hunt.getId();
        String name = hunt.getDisplayName();
        String description = hunt.getDescription();
        boolean active = hunt.isActive();
        Long startAt = hunt.getStartAt();
        Long endAt = hunt.getEndAt();
        boolean continues = hunt.isStartContinues();
        boolean archived = hunt.isArchived();
        long createdAt = hunt.getCreatedAt();
        write("""
                INSERT INTO hunts (id, display_name, description, active, start_at, end_at, start_continues, archived, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET display_name = excluded.display_name, description = excluded.description,
                  active = excluded.active, start_at = excluded.start_at, end_at = excluded.end_at,
                  start_continues = excluded.start_continues, archived = excluded.archived""",
                id, name, description, active ? 1 : 0, startAt, endAt, continues ? 1 : 0, archived ? 1 : 0, createdAt);
    }

    public void saveSetting(String huntId, String key, String value) {
        write("INSERT INTO hunt_settings (hunt_id, key, value) VALUES (?, ?, ?) "
                + "ON CONFLICT(hunt_id, key) DO UPDATE SET value = excluded.value", huntId, key, value);
    }

    public void saveSkin(Skin skin) {
        write("INSERT OR REPLACE INTO skins (id, hunt_id, texture, label, archived) VALUES (?, ?, ?, ?, ?)",
                skin.getId(), skin.getHuntId(), skin.getTexture(), skin.getLabel(), skin.isArchived() ? 1 : 0);
    }

    public void saveHead(Head head) {
        write("""
                INSERT OR REPLACE INTO heads (id, hunt_id, world, x, y, z, block_data, texture, number, clue,
                  archived, conflict, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                head.getId(), head.getHuntId(), head.getWorld(), head.getX(), head.getY(), head.getZ(),
                head.getBlockData(), head.getTexture(), head.getNumber(), head.getClue(),
                head.isArchived() ? 1 : 0, head.isConflict() ? 1 : 0, head.getCreatedBy(), head.getCreatedAt());
    }

    public void saveRun(Run run) {
        write("INSERT OR REPLACE INTO runs (id, hunt_id, number, started_at, ended_at, imported) VALUES (?, ?, ?, ?, ?, ?)",
                run.getId(), run.getHuntId(), run.getNumber(), run.getStartedAt(), run.getEndedAt(),
                run.isImported() ? 1 : 0);
    }

    public void insertFind(int runId, int headId, UUID uuid, long at) {
        write("INSERT OR IGNORE INTO finds (run_id, head_id, uuid, found_at) VALUES (?, ?, ?, ?)",
                runId, headId, uuid.toString(), at);
    }

    /** Staff reset: clears one player's finds, result and payouts for a run. */
    public void deletePlayerRun(int runId, UUID uuid) {
        write("DELETE FROM finds WHERE run_id = ? AND uuid = ?", runId, uuid.toString());
        write("DELETE FROM results WHERE run_id = ? AND uuid = ?", runId, uuid.toString());
        write("DELETE FROM payouts WHERE run_id = ? AND uuid = ?", runId, uuid.toString());
    }

    public void saveResult(PlayerResult result) {
        write("INSERT OR REPLACE INTO results (run_id, uuid, name, found, last_found_at, completed_at) VALUES (?, ?, ?, ?, ?, ?)",
                result.getRunId(), result.getUuid().toString(), result.getName(), result.getFound(),
                result.getLastFoundAt(), result.getCompletedAt());
    }

    public void saveMilestone(Milestone milestone, boolean archived) {
        write("INSERT OR REPLACE INTO milestones (id, hunt_id, threshold, message, broadcast, archived) VALUES (?, ?, ?, ?, ?, ?)",
                milestone.getId(), milestone.getHuntId(), milestone.getThreshold(), milestone.getMessage(),
                milestone.getBroadcast(), archived ? 1 : 0);
    }

    public void saveReward(Reward reward, boolean archived) {
        write("INSERT OR REPLACE INTO rewards (id, milestone_id, type, value, amount, item, archived) VALUES (?, ?, ?, ?, ?, ?, ?)",
                reward.id(), reward.milestoneId(), reward.type().name(), reward.value(), reward.amount(),
                reward.item(), archived ? 1 : 0);
    }

    public void insertPayout(int runId, int milestoneId, UUID uuid, long at) {
        write("INSERT OR IGNORE INTO payouts (run_id, milestone_id, uuid, paid_at) VALUES (?, ?, ?, ?)",
                runId, milestoneId, uuid.toString(), at);
    }

    public void audit(String actor, String huntId, String action, String detail) {
        write("INSERT INTO audit (at, actor, hunt_id, action, detail) VALUES (?, ?, ?, ?, ?)",
                System.currentTimeMillis(), actor, huntId, action, detail);
    }

    // ---------------------------------------------------------------- helpers

    private void write(String sql, Object... params) {
        writer.execute(() -> {
            synchronized (this) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    bind(ps, params);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Database write failed: " + sql.lines().findFirst().orElse(sql), e);
                }
            }
        });
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;
            switch (value) {
                case null -> ps.setNull(index, Types.NULL);
                case Integer v -> ps.setInt(index, v);
                case Long v -> ps.setLong(index, v);
                case byte[] v -> ps.setBytes(index, v);
                default -> ps.setString(index, value.toString());
            }
        }
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}

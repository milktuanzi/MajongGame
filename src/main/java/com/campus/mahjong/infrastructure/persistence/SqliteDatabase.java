package com.campus.mahjong.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite 连接与幂等建表入口。 */
public final class SqliteDatabase {
    private final String jdbcUrl;

    public SqliteDatabase(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (absolute.getParent() != null) absolute.getParent().toFile().mkdirs();
        this.jdbcUrl = "jdbc:sqlite:" + absolute;
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    public void migrate() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players (
                      id TEXT PRIMARY KEY,
                      nickname TEXT NOT NULL UNIQUE,
                      avatar_url TEXT NOT NULL DEFAULT '',
                      coins INTEGER NOT NULL DEFAULT 0,
                      level INTEGER NOT NULL DEFAULT 1,
                      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS friend_scores (
                      player_id TEXT PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
                      total_score INTEGER NOT NULL DEFAULT 0,
                      games INTEGER NOT NULL DEFAULT 0,
                      wins INTEGER NOT NULL DEFAULT 0,
                      last_played_at TEXT
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS game_records (
                      id TEXT PRIMARY KEY,
                      room_code TEXT NOT NULL,
                      mode TEXT NOT NULL,
                      round_number INTEGER NOT NULL,
                      winner_id TEXT REFERENCES players(id),
                      played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS game_score_entries (
                      game_id TEXT NOT NULL REFERENCES game_records(id) ON DELETE CASCADE,
                      player_id TEXT NOT NULL REFERENCES players(id),
                      score_delta INTEGER NOT NULL,
                      PRIMARY KEY (game_id, player_id)
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS match_ledger (
                      match_id TEXT NOT NULL, sequence INTEGER NOT NULL, round_number INTEGER NOT NULL,
                      payer TEXT, payee TEXT, amount INTEGER NOT NULL, reason TEXT NOT NULL, patterns TEXT NOT NULL,
                      PRIMARY KEY (match_id, sequence)
                    )""");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_friend_scores_rank ON friend_scores(total_score DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_game_records_time ON game_records(played_at DESC)");
        } catch (SQLException exception) {
            throw new IllegalStateException("初始化 SQLite 数据库失败", exception);
        }
    }
}

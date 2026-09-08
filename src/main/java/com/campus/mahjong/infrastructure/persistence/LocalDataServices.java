package com.campus.mahjong.infrastructure.persistence;

import com.campus.mahjong.infrastructure.persistence.repository.SqliteGameRecordRepository;
import com.campus.mahjong.infrastructure.persistence.repository.SqlitePlayerRepository;

import java.nio.file.Path;

/** 桌面版应用的数据服务组合根。 */
public final class LocalDataServices {
    private static SqliteDatabase database;
    private static SqlitePlayerRepository players;
    private static SqliteGameRecordRepository games;

    private LocalDataServices() {}

    public static synchronized void initialize(Path databaseFile) {
        if (database != null) return;
        database = new SqliteDatabase(databaseFile);
        database.migrate();
        players = new SqlitePlayerRepository(database);
        games = new SqliteGameRecordRepository(database);
    }

    public static SqlitePlayerRepository players() {
        requireInitialized(); return players;
    }

    public static SqliteGameRecordRepository games() {
        requireInitialized(); return games;
    }

    private static void requireInitialized() {
        if (database == null) throw new IllegalStateException("LocalDataServices 尚未初始化");
    }
}

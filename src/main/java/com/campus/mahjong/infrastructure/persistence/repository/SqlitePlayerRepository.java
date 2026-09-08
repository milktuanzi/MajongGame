package com.campus.mahjong.infrastructure.persistence.repository;

import com.campus.mahjong.infrastructure.persistence.SqliteDatabase;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import com.campus.mahjong.model.repository.PlayerRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlitePlayerRepository implements PlayerRepository {
    private final SqliteDatabase database;
    public SqlitePlayerRepository(SqliteDatabase database) { this.database = database; }

    @Override public void save(PlayerProfile profile) {
        String sql = """
                INSERT INTO players(id,nickname,avatar_url,coins,level) VALUES(?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET nickname=excluded.nickname, avatar_url=excluded.avatar_url,
                coins=excluded.coins, level=excluded.level, updated_at=CURRENT_TIMESTAMP""";
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.id().value());
            statement.setString(2, profile.nickname());
            statement.setString(3, profile.avatarUrl() == null ? "" : profile.avatarUrl());
            statement.setLong(4, profile.coins());
            statement.setInt(5, profile.level());
            statement.executeUpdate();
        } catch (SQLException exception) { throw failure("保存玩家失败", exception); }
    }

    @Override public Optional<PlayerProfile> findById(PlayerId id) {
        return find("SELECT * FROM players WHERE id=?", id.value());
    }

    @Override public Optional<PlayerProfile> findByNickname(String nickname) {
        return find("SELECT * FROM players WHERE nickname=?", nickname);
    }

    @Override public List<PlayerProfile> findAll() {
        List<PlayerProfile> players = new ArrayList<>();
        try (var connection = database.connect(); var statement = connection.prepareStatement("SELECT * FROM players ORDER BY nickname");
             var results = statement.executeQuery()) {
            while (results.next()) players.add(read(results));
            return players;
        } catch (SQLException exception) { throw failure("查询玩家失败", exception); }
    }

    private Optional<PlayerProfile> find(String sql, String value) {
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var results = statement.executeQuery()) { return results.next() ? Optional.of(read(results)) : Optional.empty(); }
        } catch (SQLException exception) { throw failure("查询玩家失败", exception); }
    }

    private PlayerProfile read(ResultSet results) throws SQLException {
        return new PlayerProfile(new PlayerId(results.getString("id")), results.getString("nickname"),
                results.getString("avatar_url"), results.getLong("coins"), results.getInt("level"));
    }

    private IllegalStateException failure(String message, SQLException cause) { return new IllegalStateException(message, cause); }
}

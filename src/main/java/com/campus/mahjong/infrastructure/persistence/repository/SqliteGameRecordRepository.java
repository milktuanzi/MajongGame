package com.campus.mahjong.infrastructure.persistence.repository;

import com.campus.mahjong.infrastructure.persistence.SqliteDatabase;
import com.campus.mahjong.model.common.MahjongTypes.FriendScoreEntry;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.repository.GameRecordRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 对局、明细和累计积分在同一事务中写入。recordId 保证重试不会重复计分。 */
public final class SqliteGameRecordRepository implements GameRecordRepository {
    private final SqliteDatabase database;
    public SqliteGameRecordRepository(SqliteDatabase database) { this.database = database; }

    @Override public void recordRound(String recordId, String roomCode, ModeCode mode, int round,
                                      Map<PlayerId, PlayerRoundScore> scores, Optional<PlayerId> winner) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                if (recordExists(connection, recordId)) { connection.rollback(); return; }
                upsertPlayers(connection, scores);
                try (var statement = connection.prepareStatement(
                        "INSERT INTO game_records(id,room_code,mode,round_number,winner_id) VALUES(?,?,?,?,?)")) {
                    statement.setString(1, recordId); statement.setString(2, roomCode);
                    statement.setString(3, mode.name()); statement.setInt(4, round);
                    statement.setString(5, winner.map(PlayerId::value).orElse(null)); statement.executeUpdate();
                }
                for (var entry : scores.entrySet()) {
                    try (var detail = connection.prepareStatement(
                            "INSERT INTO game_score_entries(game_id,player_id,score_delta) VALUES(?,?,?)")) {
                        detail.setString(1, recordId); detail.setString(2, entry.getKey().value());
                        detail.setLong(3, entry.getValue().delta()); detail.executeUpdate();
                    }
                    try (var ranking = connection.prepareStatement("""
                            INSERT INTO friend_scores(player_id,total_score,games,wins,last_played_at)
                            VALUES(?,?,1,?,CURRENT_TIMESTAMP)
                            ON CONFLICT(player_id) DO UPDATE SET total_score=total_score+excluded.total_score,
                            games=games+1, wins=wins+excluded.wins, last_played_at=CURRENT_TIMESTAMP""")) {
                        ranking.setString(1, entry.getKey().value()); ranking.setLong(2, entry.getValue().delta());
                        ranking.setInt(3, winner.filter(entry.getKey()::equals).isPresent() ? 1 : 0); ranking.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException exception) { throw new IllegalStateException("写入对局结算失败", exception); }
    }

    @Override public List<FriendScoreEntry> leaderboard(int limit) {
        String sql = """
                SELECT p.id,p.nickname,s.total_score,s.games,s.wins FROM friend_scores s
                JOIN players p ON p.id=s.player_id ORDER BY s.total_score DESC,s.wins DESC LIMIT ?""";
        List<FriendScoreEntry> rows = new ArrayList<>();
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (var results = statement.executeQuery()) {
                while (results.next()) rows.add(new FriendScoreEntry(new PlayerId(results.getString("id")),
                        results.getString("nickname"), results.getLong("total_score"),
                        results.getInt("games"), results.getInt("wins")));
            }
            return rows;
        } catch (SQLException exception) { throw new IllegalStateException("读取排行榜失败", exception); }
    }

    @Override public List<GameRecord> recentGames(PlayerId playerId, int limit) {
        String sql = """
                SELECT g.id,g.room_code,g.mode,g.round_number,g.winner_id,g.played_at
                FROM game_records g JOIN game_score_entries e ON e.game_id=g.id
                WHERE e.player_id=? ORDER BY g.played_at DESC LIMIT ?""";
        List<GameRecord> rows = new ArrayList<>();
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.value()); statement.setInt(2, Math.max(1, limit));
            try (var results = statement.executeQuery()) {
                while (results.next()) rows.add(new GameRecord(results.getString("id"), results.getString("room_code"),
                        ModeCode.valueOf(results.getString("mode")), results.getInt("round_number"),
                        Optional.ofNullable(results.getString("winner_id")).map(PlayerId::new), results.getString("played_at")));
            }
            return rows;
        } catch (SQLException exception) { throw new IllegalStateException("读取对局记录失败", exception); }
    }

    @Override public List<FriendScoreEntry> recentOpponents(PlayerId playerId, int limit) {
        String sql = """
                SELECT p.id,p.nickname,s.total_score,s.games,s.wins,MAX(g.played_at) AS last_seen
                FROM game_score_entries mine
                JOIN game_score_entries other ON other.game_id=mine.game_id AND other.player_id<>mine.player_id
                JOIN players p ON p.id=other.player_id
                JOIN friend_scores s ON s.player_id=p.id
                JOIN game_records g ON g.id=mine.game_id
                WHERE mine.player_id=?
                GROUP BY p.id,p.nickname,s.total_score,s.games,s.wins
                ORDER BY last_seen DESC LIMIT ?""";
        List<FriendScoreEntry> rows = new ArrayList<>();
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.value()); statement.setInt(2, Math.max(1, limit));
            try (var results = statement.executeQuery()) {
                while (results.next()) rows.add(new FriendScoreEntry(new PlayerId(results.getString("id")),
                        results.getString("nickname"), results.getLong("total_score"),
                        results.getInt("games"), results.getInt("wins")));
            }
            return rows;
        } catch (SQLException exception) { throw new IllegalStateException("读取最近对手失败", exception); }
    }

    private boolean recordExists(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM game_records WHERE id=?")) {
            statement.setString(1, id); try (var result = statement.executeQuery()) { return result.next(); }
        }
    }

    private void upsertPlayers(Connection connection, Map<PlayerId, PlayerRoundScore> scores) throws SQLException {
        for (var entry : scores.entrySet()) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO players(id,nickname) VALUES(?,?)
                    ON CONFLICT(id) DO UPDATE SET nickname=excluded.nickname,updated_at=CURRENT_TIMESTAMP""")) {
                statement.setString(1, entry.getKey().value()); statement.setString(2, entry.getValue().nickname());
                statement.executeUpdate();
            }
        }
    }
}

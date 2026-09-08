package com.campus.mahjong.infrastructure.persistence;

import com.campus.mahjong.infrastructure.persistence.repository.SqliteGameRecordRepository;
import com.campus.mahjong.infrastructure.persistence.repository.SqlitePlayerRepository;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import com.campus.mahjong.model.repository.GameRecordRepository.PlayerRoundScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void persistsPlayersAndRecordsIdempotentRankings() {
        SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"));
        database.migrate();
        var players = new SqlitePlayerRepository(database);
        var games = new SqliteGameRecordRepository(database);
        PlayerId east = new PlayerId("east");
        PlayerId south = new PlayerId("south");
        players.save(new PlayerProfile(east, "东家", "", 0, 1));
        assertTrue(players.findByNickname("东家").isPresent());
        Map<PlayerId, PlayerRoundScore> scores = new LinkedHashMap<>();
        scores.put(east, new PlayerRoundScore("东家", 24));
        scores.put(south, new PlayerRoundScore("南家", -24));
        games.recordRound("room-1-round-1", "123456", ModeCode.SICHUAN, 1, scores, Optional.of(east));
        games.recordRound("room-1-round-1", "123456", ModeCode.SICHUAN, 1, scores, Optional.of(east));
        var ranking = games.leaderboard(10);
        assertEquals(2, ranking.size());
        assertEquals(24, ranking.get(0).totalScore());
        assertEquals(1, ranking.get(0).games());
        assertEquals(1, games.recentGames(east, 10).size());
        assertEquals("南家", games.recentOpponents(east, 10).get(0).nickname());
    }
}

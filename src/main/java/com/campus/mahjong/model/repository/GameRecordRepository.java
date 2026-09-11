package com.campus.mahjong.model.repository;

import com.campus.mahjong.model.common.MahjongTypes.FriendScoreEntry;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GameRecordRepository {
    void recordRound(String recordId, String roomCode, ModeCode mode, int round,
                     Map<PlayerId, PlayerRoundScore> scores, Optional<PlayerId> winner);
    void recordRound(String recordId, String roomCode, ModeCode mode, int round,
                     Map<PlayerId, PlayerRoundScore> scores, java.util.Set<PlayerId> winners);
    void recordLedger(String matchId, List<com.campus.mahjong.model.game.ScoreEntry> entries,
                      Map<com.campus.mahjong.model.common.MahjongTypes.Seat, String> names);
    List<FriendScoreEntry> leaderboard(int limit);
    Optional<FriendScoreEntry> findScore(PlayerId playerId);
    List<FriendScoreEntry> recentOpponents(PlayerId playerId, int limit);
    List<GameRecord> recentGames(PlayerId playerId, int limit);

    record PlayerRoundScore(String nickname, long delta) {}
    record GameRecord(String recordId, String roomCode, ModeCode mode, int round,
                      Optional<PlayerId> winner, String playedAt) {}
}

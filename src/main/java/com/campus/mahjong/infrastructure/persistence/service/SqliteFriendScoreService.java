package com.campus.mahjong.infrastructure.persistence.service;

import com.campus.mahjong.model.common.MahjongTypes.FriendScoreEntry;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.repository.GameRecordRepository;
import com.campus.mahjong.model.service.room.FriendScoreService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 联网服务可直接暴露该查询服务，客户端无需访问数据库文件。 */
public final class SqliteFriendScoreService implements FriendScoreService {
    private final GameRecordRepository records;
    public SqliteFriendScoreService(GameRecordRepository records) { this.records = records; }

    @Override public CompletionStage<List<FriendScoreEntry>> leaderboard(PlayerId viewer, int limit) {
        try { return CompletableFuture.completedFuture(records.leaderboard(limit)); }
        catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }

    @Override public CompletionStage<List<FriendScoreEntry>> recentOpponents(PlayerId viewer) {
        try { return CompletableFuture.completedFuture(records.recentOpponents(viewer, 20)); }
        catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }
}

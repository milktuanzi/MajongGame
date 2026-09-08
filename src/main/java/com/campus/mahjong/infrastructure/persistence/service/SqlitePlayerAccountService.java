package com.campus.mahjong.infrastructure.persistence.service;

import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import com.campus.mahjong.model.repository.PlayerRepository;
import com.campus.mahjong.model.service.player.PlayerAccountService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SqlitePlayerAccountService implements PlayerAccountService {
    private final PlayerRepository players;
    private final PlayerId currentPlayerId;
    public SqlitePlayerAccountService(PlayerRepository players, PlayerId currentPlayerId) {
        this.players = players; this.currentPlayerId = currentPlayerId;
    }

    @Override public CompletionStage<PlayerProfile> currentPlayer() { return refresh(currentPlayerId); }

    @Override public CompletionStage<PlayerProfile> refresh(PlayerId playerId) {
        return players.findById(playerId).<CompletionStage<PlayerProfile>>map(CompletableFuture::completedFuture)
                .orElseGet(() -> CompletableFuture.failedFuture(new IllegalArgumentException("玩家不存在")));
    }
}

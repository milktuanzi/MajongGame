package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.Seat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 状态机结束后生成的领域结算，不包含页面表现信息。 */
public record RoundOutcome(Optional<Seat> winner, Optional<Seat> supplier,
                           boolean selfDraw, String reason, List<String> patterns,
                           Map<Seat, Long> scoreChanges) {
    public RoundOutcome {
        winner = winner == null ? Optional.empty() : winner;
        supplier = supplier == null ? Optional.empty() : supplier;
        patterns = List.copyOf(patterns);
        scoreChanges = Map.copyOf(scoreChanges);
    }
}

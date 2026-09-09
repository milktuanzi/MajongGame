package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.Seat;
import java.util.List;
import java.util.Optional;

/** 一笔公开积分流水；轮末记录没有付款方和收款方，金额为零。 */
public record ScoreEntry(int sequence, int round, Optional<Seat> payer, Optional<Seat> payee,
                         long amount, String reason, List<String> patterns) {
    public ScoreEntry {
        payer = payer == null ? Optional.empty() : payer;
        payee = payee == null ? Optional.empty() : payee;
        patterns = List.copyOf(patterns);
    }
}

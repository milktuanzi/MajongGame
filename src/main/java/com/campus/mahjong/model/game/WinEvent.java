package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.Seat;
import java.util.List;
import java.util.Map;

/** 一次胡牌的公开结果，跨轮保留，供四端展示胡牌张与同一笔积分变化。 */
public record WinEvent(int sequence, int round, Seat winner, TileType tile, boolean selfDraw,
                       int fan, List<String> patterns, Map<Seat, Long> scoreChanges) {
    public WinEvent { patterns = List.copyOf(patterns); scoreChanges = Map.copyOf(scoreChanges); }
}

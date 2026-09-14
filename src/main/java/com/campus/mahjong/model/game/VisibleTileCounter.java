package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.rule.region.RegionalRules;
import java.util.*;

/** 只扣除本家手牌和桌面公开牌，不读取其他玩家的暗手或牌墙顺序。 */
public final class VisibleTileCounter {
    private VisibleTileCounter() {}
    public static Map<TileType, Integer> remaining(ModeCode mode, List<TileType> visible) {
        var counts = new EnumMap<TileType, Integer>(TileType.class);
        RegionalRules.resolve(mode).buildWall().forEach(tile -> counts.merge(tile, 1, Integer::sum));
        visible.forEach(tile -> counts.computeIfPresent(tile, (key, count) -> Math.max(0, count - 1)));
        return Collections.unmodifiableMap(counts);
    }
}

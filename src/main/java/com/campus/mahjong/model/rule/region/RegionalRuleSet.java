package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.HandPatternAnalyzer;

import java.util.List;

/** 地区规则差异点；牌局状态机保持统一。 */
public interface RegionalRuleSet {
    ModeCode mode();
    String displayName();
    List<TileType> buildWall();
    boolean allowChi();
    boolean canWin(List<TileType> concealed, List<Meld> exposed);
    HandPatternAnalyzer.Analysis analyze(List<TileType> concealed, List<Meld> exposed);
}

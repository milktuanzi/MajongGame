package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.HandPatternAnalyzer;
import com.campus.mahjong.model.rule.StandardHandEvaluator;

import java.util.ArrayList;
import java.util.List;

/** 北方项目规则：136 张牌、允许吃牌，保留风牌和三元牌。 */
public final class NorthernRuleSet implements RegionalRuleSet {
    private final StandardHandEvaluator evaluator = new StandardHandEvaluator();
    private final HandPatternAnalyzer analyzer = new HandPatternAnalyzer();
    @Override public ModeCode mode() { return ModeCode.NORTHERN; }
    @Override public String displayName() { return "北方麻将"; }
    @Override public List<TileType> buildWall() { return TileType.fullWall(); }
    @Override public boolean allowChi() { return true; }
    @Override public boolean canWin(List<TileType> concealed, List<Meld> exposed) {
        return evaluator.isWinningHand(concealed, exposed.size());
    }

    @Override public HandPatternAnalyzer.Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        var base = analyzer.analyze(concealed, exposed);
        List<String> patterns = new ArrayList<>(base.patterns());
        int fan = base.fan();
        if (exposed.isEmpty()) { patterns.remove("基本胡"); patterns.add("门前清"); fan += 1; }
        return new HandPatternAnalyzer.Analysis(patterns, fan);
    }
}

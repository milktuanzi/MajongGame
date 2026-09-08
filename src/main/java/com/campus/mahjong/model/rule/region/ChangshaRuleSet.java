package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.HandPatternAnalyzer;
import com.campus.mahjong.model.rule.StandardHandEvaluator;

import java.util.ArrayList;
import java.util.List;

/** 长沙项目规则：108 张序数牌、不可吃，支持将将胡与全求人。 */
public final class ChangshaRuleSet implements RegionalRuleSet {
    private final StandardHandEvaluator evaluator = new StandardHandEvaluator();
    private final HandPatternAnalyzer analyzer = new HandPatternAnalyzer();
    @Override public ModeCode mode() { return ModeCode.CHANGSHA; }
    @Override public String displayName() { return "长沙麻将"; }
    @Override public List<TileType> buildWall() { return RuleSupport.numberedWall(); }
    @Override public boolean allowChi() { return false; }
    @Override public boolean canWin(List<TileType> concealed, List<Meld> exposed) {
        return evaluator.isWinningHand(concealed, exposed.size());
    }

    @Override public HandPatternAnalyzer.Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        var base = analyzer.analyze(concealed, exposed);
        List<String> patterns = new ArrayList<>(base.patterns());
        int fan = base.fan();
        List<TileType> all = new ArrayList<>(concealed);
        exposed.forEach(meld -> all.addAll(meld.tiles()));
        if (all.stream().allMatch(tile -> tile.rank() == 2 || tile.rank() == 5 || tile.rank() == 8)) {
            patterns.remove("基本胡"); patterns.add("将将胡"); fan += 4;
        }
        if (exposed.size() == 4 && concealed.size() == 2) {
            patterns.remove("基本胡"); patterns.add("全求人"); fan += 2;
        }
        return new HandPatternAnalyzer.Analysis(patterns, fan);
    }
}

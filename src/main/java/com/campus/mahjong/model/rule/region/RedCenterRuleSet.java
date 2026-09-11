package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.HandPatternAnalyzer;
import com.campus.mahjong.model.rule.WildcardHandEvaluator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** 四川血战基础上的 112 张四红中玩法：红中可替代任意牌，胡牌必须缺一门。 */
public final class RedCenterRuleSet implements RegionalRuleSet {
    private final WildcardHandEvaluator evaluator = new WildcardHandEvaluator();
    private final HandPatternAnalyzer analyzer = new HandPatternAnalyzer();
    @Override public ModeCode mode() { return ModeCode.RED_CENTER; }
    @Override public String displayName() { return "红中麻将"; }
    @Override public List<TileType> buildWall() { return RuleSupport.redCenterWall(); }
    @Override public boolean canWin(List<TileType> concealed, List<Meld> exposed) {
        EnumSet<TileType.Suit> suits = EnumSet.noneOf(TileType.Suit.class);
        concealed.stream().filter(TileType::suited).map(TileType::suit).forEach(suits::add);
        exposed.stream().flatMap(meld -> meld.tiles().stream()).filter(TileType::suited)
                .map(TileType::suit).forEach(suits::add);
        return suits.size() <= 2 && evaluator.isWinningHand(concealed, exposed.size(), TileType.RED);
    }

    @Override public HandPatternAnalyzer.Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        List<TileType> withoutWildcards = concealed.stream().filter(tile -> tile != TileType.RED).toList();
        var base = analyzer.analyze(withoutWildcards, exposed);
        List<String> patterns = new ArrayList<>(base.patterns());
        long count = concealed.stream().filter(tile -> tile == TileType.RED).count();
        if (count > 0) patterns.add("红中赖子×" + count);
        if (count == 4) patterns.add("四红中");
        return new HandPatternAnalyzer.Analysis(patterns, base.fan() + (count == 4 ? 2 : 0));
    }
}

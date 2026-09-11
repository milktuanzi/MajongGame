package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.HandPatternAnalyzer;
import com.campus.mahjong.model.rule.StandardHandEvaluator;

import java.util.EnumSet;
import java.util.List;

/** 四川血战基础规则：108 张序数牌，胡牌时必须缺一门。 */
public final class SichuanRuleSet implements RegionalRuleSet {
    private final StandardHandEvaluator evaluator = new StandardHandEvaluator();
    private final HandPatternAnalyzer analyzer = new HandPatternAnalyzer();
    @Override public ModeCode mode() { return ModeCode.SICHUAN; }
    @Override public String displayName() { return "四川麻将"; }
    @Override public List<TileType> buildWall() { return RuleSupport.numberedWall(); }
    @Override public boolean canWin(List<TileType> concealed, List<Meld> exposed) {
        EnumSet<TileType.Suit> suits = EnumSet.noneOf(TileType.Suit.class);
        concealed.stream().filter(TileType::suited).map(TileType::suit).forEach(suits::add);
        exposed.stream().flatMap(meld -> meld.tiles().stream()).filter(TileType::suited)
                .map(TileType::suit).forEach(suits::add);
        return suits.size() <= 2 && evaluator.isWinningHand(concealed, exposed.size());
    }

    @Override public HandPatternAnalyzer.Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        var base = analyzer.analyze(concealed, exposed);
        return new HandPatternAnalyzer.Analysis(base.patterns(), base.fan());
    }
}

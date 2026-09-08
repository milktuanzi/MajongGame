package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.TileType;

import java.util.ArrayList;
import java.util.List;

/** 将指定牌作为万能牌进行胡牌判定；以非递减替换组合避免重复枚举。 */
public final class WildcardHandEvaluator {
    private final StandardHandEvaluator standard = new StandardHandEvaluator();

    public boolean isWinningHand(List<TileType> concealed, int exposedMeldCount, TileType wildcard) {
        int wildcards = (int) concealed.stream().filter(wildcard::equals).count();
        if (wildcards == 0) return standard.isWinningHand(concealed, exposedMeldCount);
        List<TileType> fixed = new ArrayList<>(concealed);
        fixed.removeIf(wildcard::equals);
        TileType[] candidates = java.util.Arrays.stream(TileType.values())
                .filter(tile -> tile != wildcard).toArray(TileType[]::new);
        return substitute(fixed, exposedMeldCount, candidates, wildcards, 0);
    }

    private boolean substitute(List<TileType> hand, int exposedMeldCount, TileType[] candidates,
                               int remaining, int startIndex) {
        if (remaining == 0) return standard.isWinningHand(hand, exposedMeldCount);
        for (int index = startIndex; index < candidates.length; index++) {
            hand.add(candidates[index]);
            if (substitute(hand, exposedMeldCount, candidates, remaining - 1, index)) return true;
            hand.remove(hand.size() - 1);
        }
        return false;
    }
}

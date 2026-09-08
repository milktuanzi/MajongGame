package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.TileType;

import java.util.List;

/** 标准四面子一雀头及七对判定，可被不同地方规则组合复用。 */
public final class StandardHandEvaluator {
    public boolean isWinningHand(List<TileType> concealedTiles, int exposedMeldCount) {
        int requiredConcealed = 14 - exposedMeldCount * 3;
        if (concealedTiles.size() != requiredConcealed) return false;
        int[] counts = counts(concealedTiles);
        if (exposedMeldCount == 0 && isSevenPairs(counts)) return true;
        for (int pair = 0; pair < counts.length; pair++) {
            if (counts[pair] < 2) continue;
            counts[pair] -= 2;
            if (canFormMelds(counts)) {
                counts[pair] += 2;
                return true;
            }
            counts[pair] += 2;
        }
        return false;
    }

    private int[] counts(List<TileType> tiles) {
        int[] result = new int[TileType.values().length];
        tiles.forEach(tile -> result[tile.ordinal()]++);
        return result;
    }

    private boolean isSevenPairs(int[] counts) {
        int pairs = 0;
        for (int count : counts) {
            if (count == 2) pairs++;
            else if (count == 4) pairs += 2;
            else if (count != 0) return false;
        }
        return pairs == 7;
    }

    private boolean canFormMelds(int[] counts) {
        int first = -1;
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0) { first = index; break; }
        }
        if (first < 0) return true;
        if (counts[first] >= 3) {
            counts[first] -= 3;
            if (canFormMelds(counts)) { counts[first] += 3; return true; }
            counts[first] += 3;
        }
        TileType tile = TileType.values()[first];
        if (tile.suited() && tile.rank() <= 7) {
            TileType second = TileType.values()[first + 1];
            TileType third = TileType.values()[first + 2];
            if (second.suit() == tile.suit() && third.suit() == tile.suit()
                    && counts[first + 1] > 0 && counts[first + 2] > 0) {
                counts[first]--; counts[first + 1]--; counts[first + 2]--;
                if (canFormMelds(counts)) {
                    counts[first]++; counts[first + 1]++; counts[first + 2]++;
                    return true;
                }
                counts[first]++; counts[first + 1]++; counts[first + 2]++;
            }
        }
        return false;
    }
}

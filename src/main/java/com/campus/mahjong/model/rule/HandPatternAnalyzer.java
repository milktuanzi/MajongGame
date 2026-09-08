package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** 计算所有玩法均可复用的基础牌型；地方番型由各 ModeCode 规则引擎追加。 */
public final class HandPatternAnalyzer {
    public Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        List<String> patterns = new ArrayList<>();
        int fan = 1;
        if (exposed.isEmpty() && isSevenPairs(concealed)) {
            patterns.add("七对");
            fan = Math.max(fan, 4);
        }
        EnumSet<TileType.Suit> numberedSuits = EnumSet.noneOf(TileType.Suit.class);
        boolean honors = false;
        List<TileType> allTiles = new ArrayList<>(concealed);
        exposed.forEach(meld -> allTiles.addAll(meld.tiles()));
        for (TileType tile : allTiles) {
            if (tile.suited()) numberedSuits.add(tile.suit());
            else honors = true;
        }
        if (numberedSuits.size() == 1 && !honors) {
            patterns.add("清一色");
            fan += 4;
        } else if (numberedSuits.size() == 1) {
            patterns.add("混一色");
            fan += 2;
        }
        if (isAllTriplets(concealed) && exposed.stream().noneMatch(meld -> meld.type().name().equals("CHI"))) {
            patterns.add("碰碰胡");
            fan += 2;
        }
        if (patterns.isEmpty()) patterns.add("基本胡");
        return new Analysis(List.copyOf(patterns), fan);
    }

    private boolean isSevenPairs(List<TileType> hand) {
        if (hand.size() != 14) return false;
        int[] counts = counts(hand);
        int pairs = 0;
        for (int count : counts) {
            if (count == 2) pairs++;
            else if (count == 4) pairs += 2;
            else if (count != 0) return false;
        }
        return pairs == 7;
    }

    private boolean isAllTriplets(List<TileType> concealed) {
        int[] counts = counts(concealed);
        for (int pair = 0; pair < counts.length; pair++) {
            if (counts[pair] < 2) continue;
            counts[pair] -= 2;
            boolean triplets = true;
            for (int count : counts) if (count % 3 != 0) { triplets = false; break; }
            counts[pair] += 2;
            if (triplets) return true;
        }
        return false;
    }

    private int[] counts(List<TileType> tiles) {
        int[] counts = new int[TileType.values().length];
        tiles.forEach(tile -> counts[tile.ordinal()]++);
        return counts;
    }

    public record Analysis(List<String> patterns, int fan) {}
}

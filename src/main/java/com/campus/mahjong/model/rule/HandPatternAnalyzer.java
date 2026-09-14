package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import java.util.*;

/** 川麻本房番表：平胡 0 番，组合番型取总番，不重复叠加已包含的低阶牌型。 */
public final class HandPatternAnalyzer {
    public static final String FAN_TABLE = "底分 10 · 每加 1 番翻倍\n平胡 0 番；碰碰胡 1 番；清一色、金钩钓、七对、幺九 2 番；"
            + "龙七对、将对 3 番；清金钩钓、清七对、双龙七对 4 番；十八罗汉 6 番；将十八罗汉 7 番。\n"
            + "清一色组合增加 2 番（如清碰 3 番、清龙七对 5 番），已包含番型不重复加算。"
            + "龙七对中的四张算两对，不得已经碰杠；幺九要求每副面子和对子都含 1 或 9。\n"
            + "每位付款者：10 × 房间倍率 × 2^番数，再按房间分数封顶。点炮者付一份，自摸由其余未胡者各付一份。";

    public Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        if (!new StandardHandEvaluator().isWinningHand(concealed, exposed.size()))
            throw new IllegalArgumentException("只有完整胡牌才能计算番型");
        var all = new ArrayList<>(concealed); exposed.forEach(m -> all.addAll(m.tiles()));
        boolean pure = all.stream().allMatch(t -> t.suited() && t.suit() == all.getFirst().suit());
        boolean jiang = all.stream().allMatch(t -> t.suited() && (t.rank() == 2 || t.rank() == 5 || t.rank() == 8));
        int[] counts = counts(concealed);
        Analysis best = new Analysis(List.of(pure ? "清一色" : "平胡"), pure ? 2 : 0);
        if (exposed.isEmpty() && concealed.size() == 14 && Arrays.stream(counts).allMatch(n -> n % 2 == 0)) {
            int dragons = (int) Arrays.stream(counts).filter(n -> n == 4).count();
            String name = switch (dragons) { case 0 -> "七对"; case 1 -> "龙七对"; case 2 -> "双龙七对"; default -> "三龙七对"; };
            best = new Analysis(List.of(pure ? "清" + name : name), 2 + dragons + (pure ? 2 : 0));
        }
        boolean triplets = exposed.stream().allMatch(m -> m.type() == PlayerActionType.PENG || m.type() == PlayerActionType.GANG)
                && hasTripletDecomposition(counts);
        if (triplets) {
            boolean gold = exposed.size() == 4 && concealed.size() == 2;
            boolean eighteen = gold && exposed.stream().allMatch(m -> m.type() == PlayerActionType.GANG && m.tiles().size() == 4);
            int fan = eighteen ? (jiang ? 7 : 6) : jiang ? 3 : gold ? 2 : 1;
            String name = eighteen ? (jiang ? "将十八罗汉" : "十八罗汉") : jiang ? "将对" : gold ? "金钩钓" : "碰碰胡";
            if (pure) { fan += 2; name = name.equals("碰碰胡") ? "清碰" : "清" + name; }
            if (fan > best.fan()) best = new Analysis(List.of(name), fan);
        }
        if (exposed.stream().allMatch(m -> m.tiles().stream().anyMatch(this::terminal)) && hasTerminalDecomposition(counts)) {
            int fan = 2 + (pure ? 2 : 0);
            if (fan > best.fan()) best = new Analysis(List.of(pure ? "清幺九" : "幺九"), fan);
        }
        return best;
    }
    private boolean terminal(TileType tile) { return tile.suited() && (tile.rank() == 1 || tile.rank() == 9); }
    private int[] counts(List<TileType> tiles) {
        int[] result = new int[TileType.values().length]; tiles.forEach(t -> result[t.ordinal()]++); return result;
    }
    private boolean hasTripletDecomposition(int[] counts) {
        for (int pair = 0; pair < counts.length; pair++) {
            if (counts[pair] < 2) continue;
            counts[pair] -= 2; boolean valid = Arrays.stream(counts).allMatch(n -> n % 3 == 0); counts[pair] += 2;
            if (valid) return true;
        }
        return false;
    }
    private boolean hasTerminalDecomposition(int[] counts) {
        for (int pair = 0; pair < counts.length; pair++) {
            if (counts[pair] < 2 || !terminal(TileType.values()[pair])) continue;
            counts[pair] -= 2; boolean valid = terminalMelds(counts); counts[pair] += 2;
            if (valid) return true;
        }
        return false;
    }
    private boolean terminalMelds(int[] counts) {
        int first = 0; while (first < counts.length && counts[first] == 0) first++;
        if (first == counts.length) return true;
        TileType tile = TileType.values()[first];
        if (terminal(tile) && counts[first] >= 3) {
            counts[first] -= 3; boolean valid = terminalMelds(counts); counts[first] += 3;
            if (valid) return true;
        }
        if (tile.suited() && (tile.rank() == 1 || tile.rank() == 7) && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--; counts[first + 1]--; counts[first + 2]--;
            boolean valid = terminalMelds(counts);
            counts[first]++; counts[first + 1]++; counts[first + 2]++;
            return valid;
        }
        return false;
    }
    public record Analysis(List<String> patterns, int fan) {
        public Analysis { patterns = List.copyOf(patterns); }
    }
}

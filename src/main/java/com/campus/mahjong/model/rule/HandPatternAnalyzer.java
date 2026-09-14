package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.TileType;
import java.util.*;

/** 按确认番表识别静态牌型；互相包含的牌型只保留最高项，根另行逐根累加。 */
public final class HandPatternAnalyzer {
    public static final String FAN_TABLE = "底分 10 · 平胡 0 番 · 每加 1 番翻倍\n"
            + "1番：碰碰胡、断幺九、海底捞月、杠上炮、杠上开花、自摸；每有一根另加1番。\n"
            + "2番：金钩钓、七对、清一色、将将胡；3番：将对、龙七对；"
            + "4番：清金钩钓、将七对；5番：天胡、地胡、清龙七对、三龙七对、将龙七对；\n"
            + "6番：将双龙七对、清双龙七对；7番：十八罗汉、将三龙七对、清三龙七对、九莲宝灯；"
            + "8番：连七对；9番：清十八罗汉、将十八罗汉。门前清不计。\n"
            + "杠牌即时流水：点杠、暗杠1番，补杠0番。";

    public Analysis analyze(List<TileType> concealed, List<Meld> exposed) {
        if (!new StandardHandEvaluator().isWinningHand(concealed, exposed.size()))
            throw new IllegalArgumentException("只有完整胡牌才能计算番型");
        List<TileType> all = new ArrayList<>(concealed); exposed.forEach(m -> all.addAll(m.tiles()));
        boolean pure = all.stream().allMatch(TileType::suited) && all.stream().map(TileType::suit).distinct().count() == 1;
        boolean jiang = all.stream().allMatch(t -> t.suited() && (t.rank() == 2 || t.rank() == 5 || t.rank() == 8));
        int[] counts = counts(concealed);
        boolean sevenPairs = exposed.isEmpty() && concealed.size() == 14 && Arrays.stream(counts).allMatch(n -> n % 2 == 0);
        int dragons = sevenPairs ? (int) Arrays.stream(counts).filter(n -> n == 4).count() : 0;
        boolean triplets = exposed.stream().allMatch(m -> m.type() == PlayerActionType.PENG || m.type() == PlayerActionType.GANG)
                && hasTripletDecomposition(counts);
        boolean gold = triplets && exposed.size() == 4 && concealed.size() == 2;
        boolean eighteen = gold && exposed.stream().allMatch(m -> m.type() == PlayerActionType.GANG && m.tiles().size() == 4);
        Analysis best = new Analysis(List.of("平胡"), 0);
        if (all.stream().allMatch(t -> t.suited() && t.rank() != 1 && t.rank() != 9)) best = choose(best, "断幺九", 1);
        if (triplets) best = choose(best, "碰碰胡", 1);
        if (pure) best = choose(best, "清一色", 2);
        if (jiang) best = choose(best, "将将胡", 2);
        if (gold) best = choose(best, pure ? "清金钩钓" : "金钩钓", pure ? 4 : 2);
        if (triplets && jiang) best = choose(best, "将对", 3);
        if (sevenPairs) {
            best = choose(best, "七对", 2);
            if (jiang) best = choose(best, dragons == 0 ? "将七对" : dragons == 1 ? "将龙七对" : dragons == 2 ? "将双龙七对" : "将三龙七对", 4 + dragons);
            else if (pure) best = choose(best, dragons == 0 ? "清七对" : dragons == 1 ? "清龙七对" : dragons == 2 ? "清双龙七对" : "清三龙七对", dragons == 0 ? 4 : 4 + dragons);
            else if (dragons > 0) best = choose(best, dragons == 1 ? "龙七对" : dragons == 2 ? "双龙七对" : "三龙七对", dragons == 1 ? 3 : dragons == 2 ? 4 : 5);
            if (isConsecutiveSevenPairs(counts)) best = choose(best, "连七对", 8);
        }
        if (isNineGates(counts, exposed)) best = choose(best, "九莲宝灯", 7);
        if (eighteen) best = choose(best, jiang ? "将十八罗汉" : pure ? "清十八罗汉" : "十八罗汉", jiang || pure ? 9 : 7);
        return best;
    }

    public Analysis addRoots(Analysis base, List<TileType> actualConcealed, List<Meld> exposed) {
        int[] all = counts(actualConcealed); exposed.forEach(m -> m.tiles().forEach(t -> all[t.ordinal()]++));
        int roots = (int) Arrays.stream(all).filter(n -> n == 4).count();
        if (roots == 0) return base;
        List<String> patterns = new ArrayList<>(base.patterns()); patterns.add("根×" + roots);
        return new Analysis(patterns, base.fan() + roots);
    }
    private Analysis choose(Analysis current, String name, int fan) { return fan > current.fan() ? new Analysis(List.of(name), fan) : current; }
    private int[] counts(List<TileType> tiles) { int[] c = new int[TileType.values().length]; tiles.forEach(t -> c[t.ordinal()]++); return c; }
    private boolean hasTripletDecomposition(int[] counts) {
        for (int pair = 0; pair < counts.length; pair++) { if (counts[pair] < 2) continue; counts[pair] -= 2; boolean valid = Arrays.stream(counts).allMatch(n -> n % 3 == 0); counts[pair] += 2; if (valid) return true; } return false;
    }
    private boolean isConsecutiveSevenPairs(int[] counts) {
        int first = -1, pairs = 0; for (int i = 0; i < 27; i++) if (counts[i] == 2) { if (first < 0) first = i; pairs++; }
        if (pairs != 7 || first < 0 || first / 9 != (first + 6) / 9) return false;
        for (int i = first; i < first + 7; i++) if (counts[i] != 2) return false; return true;
    }
    private boolean isNineGates(int[] counts, List<Meld> exposed) {
        if (!exposed.isEmpty()) return false;
        for (int suit = 0; suit < 3; suit++) { int o = suit * 9, total = 0; for (int i=o;i<o+9;i++) total += counts[i]; if (total != 14 || counts[o] < 3 || counts[o+8] < 3) continue; boolean middle=true; for(int i=o+1;i<o+8;i++) middle &= counts[i]>=1; if(middle) return true; } return false;
    }
    public record Analysis(List<String> patterns, int fan) { public Analysis { patterns = List.copyOf(patterns); } }
}

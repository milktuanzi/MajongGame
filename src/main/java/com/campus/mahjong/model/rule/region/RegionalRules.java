package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;

import java.util.EnumMap;
import java.util.Map;

/** 地区规则注册表。 */
public final class RegionalRules {
    private static final Map<ModeCode, RegionalRuleSet> RULES = createRules();

    private RegionalRules() {}

    public static RegionalRuleSet resolve(ModeCode mode) {
        RegionalRuleSet rule = RULES.get(mode);
        if (rule == null) throw new IllegalArgumentException("未注册玩法: " + mode);
        return rule;
    }

    private static Map<ModeCode, RegionalRuleSet> createRules() {
        EnumMap<ModeCode, RegionalRuleSet> rules = new EnumMap<>(ModeCode.class);
        register(rules, new SichuanRuleSet());
        register(rules, new ChangshaRuleSet());
        register(rules, new NorthernRuleSet());
        register(rules, new RedCenterRuleSet());
        return Map.copyOf(rules);
    }

    private static void register(Map<ModeCode, RegionalRuleSet> rules, RegionalRuleSet rule) {
        rules.put(rule.mode(), rule);
    }
}

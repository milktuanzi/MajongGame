package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import java.util.Set;

/** 根据玩法选择四川、长沙、北方或红中规则实现。 */
public interface RuleEngineRegistry {
    MahjongRuleEngine resolve(ModeCode mode);
    Set<ModeCode> supportedModes();
}

package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.util.List;

/** 各地麻将规则插件的统一领域接口，不依赖 JavaFX 或网络层。 */
public interface MahjongRuleEngine {
    ModeCode supports();
    void initialize(RoomSnapshot room, long randomSeed);
    List<ActionOption> legalActions(GameSnapshot state, PlayerId playerId);
    ActionResult apply(GameSnapshot state, ActionRequest action);
    boolean isRoundFinished(GameSnapshot state);
    Settlement settle(GameSnapshot state);
}

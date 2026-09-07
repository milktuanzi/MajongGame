package com.campus.mahjong.model.service.player;

import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import java.util.concurrent.CompletionStage;

/** 主界面基本信息的数据来源。 */
public interface PlayerAccountService {
    CompletionStage<PlayerProfile> currentPlayer();
    CompletionStage<PlayerProfile> refresh(PlayerId playerId);
}

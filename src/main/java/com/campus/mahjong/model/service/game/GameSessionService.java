package com.campus.mahjong.model.service.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** 牌局页面的用例入口；expectedRevision 用于拒绝过期操作。 */
public interface GameSessionService {
    CompletionStage<GameSnapshot> snapshot(GameId gameId, PlayerId viewer);
    CompletionStage<List<ActionOption>> availableActions(GameId gameId, PlayerId playerId);
    CompletionStage<ActionResult> perform(ActionRequest request);
    CompletionStage<Settlement> latestSettlement(GameId gameId);
}

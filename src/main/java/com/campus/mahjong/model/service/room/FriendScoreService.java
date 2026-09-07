package com.campus.mahjong.model.service.room;

import com.campus.mahjong.model.common.MahjongTypes.FriendScoreEntry;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** 查询好友群组的累计积分榜及个人对局记录。 */
public interface FriendScoreService {
    CompletionStage<List<FriendScoreEntry>> leaderboard(PlayerId viewer, int limit);
    CompletionStage<List<FriendScoreEntry>> recentOpponents(PlayerId viewer);
}

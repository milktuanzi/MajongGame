package com.campus.mahjong.model.service.room;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** 好友房创建、加入及开局前管理契约。 */
public interface FriendRoomService {
    CompletionStage<FriendRoomAccess> create(PlayerId ownerId, FriendRoomSettings settings);
    CompletionStage<FriendRoomAccess> joinByInviteCode(PlayerId playerId, String inviteCode, String password);
    CompletionStage<RoomSnapshot> setReady(RoomId roomId, PlayerId playerId, boolean ready);
    CompletionStage<RoomSnapshot> updateSettings(RoomId roomId, PlayerId ownerId, FriendRoomSettings settings);
    CompletionStage<Void> leave(RoomId roomId, PlayerId playerId);
    CompletionStage<GameId> start(RoomId roomId, PlayerId ownerId);
    CompletionStage<FriendRoomAccess> reconnect(PlayerId playerId, String reconnectToken);

    /** 订阅服务端房间快照；实现可基于 WebSocket 或 TCP 长连接。 */
    AutoCloseable observe(RoomId roomId, Consumer<RoomSnapshot> listener);
}

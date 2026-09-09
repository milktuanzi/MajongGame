package com.campus.mahjong.infrastructure.network.protocol;

import com.campus.mahjong.model.common.MahjongTypes.*;

import java.util.List;

/** JSON payload；保持为简单 record，便于协议往返测试。 */
public final class Payloads {
    private Payloads() {}

    public record CreateRoom(PlayerProfile player, FriendRoomSettings settings) {}
    public record JoinRoom(PlayerProfile player, String inviteCode, String password) {}
    public record Ready(boolean ready) {}
    public record PlayerAction(GameId gameId, PlayerActionType type, List<Tile> tiles) {
        public PlayerAction {
            java.util.Objects.requireNonNull(gameId, "gameId");
            tiles = List.copyOf(tiles);
        }
    }
    public record Response(boolean accepted, String message, String reconnectToken,
                           String inviteCode, String gameId, RoomSnapshot room) {
        public Response {
            if (message == null) message = "";
            if (reconnectToken == null) reconnectToken = "";
            if (inviteCode == null) inviteCode = "";
            if (gameId == null) gameId = "";
        }
    }
}

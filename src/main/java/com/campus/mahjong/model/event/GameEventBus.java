package com.campus.mahjong.model.event;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.time.Instant;

/** 服务层向 JavaFX 控制器推送房间和牌局变化。 */
public interface GameEventBus {
    Subscription subscribe(GameEventListener listener);
    void publish(GameEvent event);

    interface Subscription extends AutoCloseable { @Override void close(); }
    interface GameEventListener { void onEvent(GameEvent event); }

    sealed interface GameEvent permits RoomChanged, GameChanged, RoundSettled, ConnectionChanged {
        Instant occurredAt();
    }
    record RoomChanged(RoomSnapshot room, Instant occurredAt) implements GameEvent {}
    record GameChanged(GameSnapshot game, Instant occurredAt) implements GameEvent {}
    record RoundSettled(Settlement settlement, Instant occurredAt) implements GameEvent {}
    record ConnectionChanged(PlayerId playerId, boolean connected, Instant occurredAt) implements GameEvent {}
}

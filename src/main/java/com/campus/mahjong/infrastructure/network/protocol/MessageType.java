package com.campus.mahjong.infrastructure.network.protocol;

/** 热点个域网第一阶段支持的消息类型。 */
public enum MessageType {
    CREATE_ROOM,
    JOIN_ROOM,
    SET_READY,
    START_GAME,
    NEXT_ROUND,
    ROUND_SETTLED,
    LEAVE_ROOM,
    PLAYER_ACTION,
    ROOM_SNAPSHOT,
    GAME_SNAPSHOT,
    RESPONSE,
    PING,
    PONG
}

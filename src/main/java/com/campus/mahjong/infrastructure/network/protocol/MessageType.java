package com.campus.mahjong.infrastructure.network.protocol;

/** 热点个域网第一阶段支持的消息类型。 */
public enum MessageType {
    CREATE_ROOM,
    JOIN_ROOM,
    SET_READY,
    ADD_BOT,
    REMOVE_BOT,
    TEACHER_LESSON,
    START_GAME,
    LEAVE_ROOM,
    PLAYER_ACTION,
    ROOM_SNAPSHOT,
    GAME_SNAPSHOT,
    RESPONSE,
    PING,
    PONG
}

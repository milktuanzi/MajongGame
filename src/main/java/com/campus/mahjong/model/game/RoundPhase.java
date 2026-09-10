package com.campus.mahjong.model.game;

/** 一局牌的权威状态；任何操作都必须符合当前阶段。 */
public enum RoundPhase {
    EXCHANGING_TILES,
    CHOOSING_MISSING_SUIT,
    WAITING_FOR_DRAW,
    WAITING_FOR_DISCARD,
    WAITING_FOR_CLAIMS,
    FINISHED
}

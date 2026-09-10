package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.Seat;

/** Seat 顺序为东、南、西、北，桌面上对应逆时针。 */
public enum ExchangeDirection {
    CLOCKWISE(3, "顺时针"), COUNTERCLOCKWISE(1, "逆时针"), OPPOSITE(2, "对家");
    private final int offset;
    private final String label;
    ExchangeDirection(int offset, String label) { this.offset = offset; this.label = label; }
    public Seat recipient(Seat sender) { return Seat.values()[(sender.ordinal() + offset) % 4]; }
    public String label() { return label; }
}

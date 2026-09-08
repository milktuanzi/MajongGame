package com.campus.mahjong.view.component;

import com.campus.mahjong.model.common.MahjongTypes.Seat;

/**
 * 每个客户端均以本机玩家为桌面底部，按牌局逆时针顺序映射其余三家。
 * 联机快照只需提供本机 Seat，即可复用同一份 FXML 布局。
 */
public final class TableSeatLayout {
    public enum Position { BOTTOM, RIGHT, TOP, LEFT }

    private TableSeatLayout() { }

    public static Seat seatAt(Seat localSeat, Position position) {
        int offset = switch (position) {
            case BOTTOM -> 0;
            case RIGHT -> 1;
            case TOP -> 2;
            case LEFT -> 3;
        };
        Seat[] seats = Seat.values();
        return seats[(localSeat.ordinal() + offset) % seats.length];
    }
}

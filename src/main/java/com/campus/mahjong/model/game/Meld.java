package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.common.MahjongTypes.Seat;

import java.util.List;

public record Meld(PlayerActionType type, List<TileType> tiles, Seat fromSeat) {
    public Meld { tiles = List.copyOf(tiles); }
}

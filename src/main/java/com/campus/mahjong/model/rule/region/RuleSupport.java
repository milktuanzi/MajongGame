package com.campus.mahjong.model.rule.region;

import com.campus.mahjong.model.game.TileType;

import java.util.List;

final class RuleSupport {
    private RuleSupport() {}

    static List<TileType> numberedWall() {
        return TileType.fullWall().stream().filter(TileType::suited).toList();
    }

    static List<TileType> redCenterWall() {
        return TileType.fullWall().stream().filter(tile -> tile.suited() || tile == TileType.RED).toList();
    }
}

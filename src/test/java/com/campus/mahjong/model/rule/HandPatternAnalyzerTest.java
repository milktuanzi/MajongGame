package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.TileType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandPatternAnalyzerTest {
    @Test
    void detectsSevenPairsAndPureSuit() {
        var hand = List.of(
                TileType.MAN_1, TileType.MAN_1, TileType.MAN_2, TileType.MAN_2,
                TileType.MAN_3, TileType.MAN_3, TileType.MAN_4, TileType.MAN_4,
                TileType.MAN_5, TileType.MAN_5, TileType.MAN_7, TileType.MAN_7,
                TileType.MAN_9, TileType.MAN_9);
        var analysis = new HandPatternAnalyzer().analyze(hand, List.of());
        assertEquals(List.of("清七对"), analysis.patterns());
        assertEquals(4, analysis.fan());
    }
}

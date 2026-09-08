package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.region.RegionalRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalRuleSetTest {
    @Test
    void buildsExpectedRegionalWalls() {
        assertEquals(108, RegionalRules.resolve(ModeCode.SICHUAN).buildWall().size());
        assertEquals(108, RegionalRules.resolve(ModeCode.CHANGSHA).buildWall().size());
        assertEquals(136, RegionalRules.resolve(ModeCode.NORTHERN).buildWall().size());
        assertEquals(112, RegionalRules.resolve(ModeCode.RED_CENTER).buildWall().size());
        assertTrue(RegionalRules.resolve(ModeCode.NORTHERN).allowChi());
        assertFalse(RegionalRules.resolve(ModeCode.SICHUAN).allowChi());
    }

    @Test
    void redCenterActsAsWildcard() {
        var hand = List.of(
                TileType.MAN_1, TileType.MAN_2, TileType.RED,
                TileType.MAN_4, TileType.MAN_5, TileType.MAN_6,
                TileType.PIN_2, TileType.PIN_3, TileType.PIN_4,
                TileType.SOU_7, TileType.SOU_8, TileType.SOU_9,
                TileType.MAN_9, TileType.MAN_9);
        assertTrue(RegionalRules.resolve(ModeCode.RED_CENTER).canWin(hand, List.of()));
    }

    @Test
    void sichuanRequiresMissingOneSuit() {
        var threeSuitHand = List.of(
                TileType.MAN_1, TileType.MAN_2, TileType.MAN_3,
                TileType.MAN_4, TileType.MAN_5, TileType.MAN_6,
                TileType.PIN_2, TileType.PIN_3, TileType.PIN_4,
                TileType.SOU_7, TileType.SOU_8, TileType.SOU_9,
                TileType.MAN_9, TileType.MAN_9);
        assertFalse(RegionalRules.resolve(ModeCode.SICHUAN).canWin(threeSuitHand, List.of()));
    }
}

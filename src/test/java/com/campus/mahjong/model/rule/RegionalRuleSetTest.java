package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.game.MahjongRound;
import com.campus.mahjong.model.game.RoundPhase;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.region.RegionalRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalRuleSetTest {
    @Test
    void buildsExpectedRegionalWalls() {
        assertEquals(108, RegionalRules.resolve(ModeCode.SICHUAN).buildWall().size());
        assertEquals(112, RegionalRules.resolve(ModeCode.RED_CENTER).buildWall().size());
        assertEquals(4, RegionalRules.resolve(ModeCode.RED_CENTER).buildWall().stream()
                .filter(tile -> tile == TileType.RED).count());
    }

    @Test
    void redCenterActsAsWildcard() {
        var hand = List.of(
                TileType.MAN_1, TileType.MAN_2, TileType.RED,
                TileType.MAN_4, TileType.MAN_5, TileType.MAN_6,
                TileType.PIN_2, TileType.PIN_3, TileType.PIN_4,
                TileType.PIN_7, TileType.PIN_8, TileType.PIN_9,
                TileType.MAN_9, TileType.MAN_9);
        assertTrue(RegionalRules.resolve(ModeCode.RED_CENTER).canWin(hand, List.of()));
    }

    @Test
    void redCenterKeepsSichuanOpeningAndMissingSuitRestriction() {
        var round = MahjongRound.start(new FriendRoomSettings(
                ModeCode.RED_CENTER, 2, Optional.of(128), 4, false, ""), 42);
        assertEquals(RoundPhase.EXCHANGING_TILES, round.phase());
        assertEquals(59, round.wallRemaining());
        var threeSuitHand = List.of(
                TileType.MAN_1, TileType.MAN_2, TileType.RED,
                TileType.MAN_4, TileType.MAN_5, TileType.MAN_6,
                TileType.PIN_2, TileType.PIN_3, TileType.PIN_4,
                TileType.SOU_7, TileType.SOU_8, TileType.SOU_9,
                TileType.MAN_9, TileType.MAN_9);
        assertFalse(RegionalRules.resolve(ModeCode.RED_CENTER).canWin(threeSuitHand, List.of()));
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

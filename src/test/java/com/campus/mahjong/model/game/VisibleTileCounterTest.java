package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.campus.mahjong.model.game.TileType.*;
import static org.junit.jupiter.api.Assertions.*;

class VisibleTileCounterTest {
    @Test void subtractsOwnHandDiscardsAndAllFourGangTiles() {
        var result = VisibleTileCounter.remaining(ModeCode.SICHUAN,
                List.of(MAN_1, MAN_1, PIN_2, PIN_2, PIN_2, SOU_9, SOU_9, SOU_9, SOU_9));
        assertEquals(27, result.size());
        assertEquals(2, result.get(MAN_1));
        assertEquals(1, result.get(PIN_2));
        assertEquals(0, result.get(SOU_9));
        assertEquals(4, result.get(MAN_9));
        assertEquals(99, result.values().stream().mapToInt(Integer::intValue).sum());
    }
    @Test void movingDiscardIntoPengDoesNotSubtractClaimedTileTwice() {
        var before = VisibleTileCounter.remaining(ModeCode.SICHUAN, List.of(MAN_1));
        // 被碰的弃牌从弃牌区移入三张副露；公开总量由一张变三张。
        var after = VisibleTileCounter.remaining(ModeCode.SICHUAN, List.of(MAN_1, MAN_1, MAN_1));
        assertEquals(3, before.get(MAN_1)); assertEquals(1, after.get(MAN_1));
    }
    @Test void redCenterIsIncludedOnlyInItsModeAndNewRoundRecomputesCounts() {
        assertFalse(VisibleTileCounter.remaining(ModeCode.SICHUAN, List.of()).containsKey(RED));
        assertEquals(3, VisibleTileCounter.remaining(ModeCode.RED_CENTER, List.of(RED)).get(RED));
        assertEquals(112, VisibleTileCounter.remaining(ModeCode.RED_CENTER, List.of()).values().stream().mapToInt(Integer::intValue).sum());
    }
}

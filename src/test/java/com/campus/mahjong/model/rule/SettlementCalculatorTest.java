package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementCalculatorTest {
    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    void selfDrawUsesBaseScoreRoomMultiplierAndPatternMultiplier() {
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.SICHUAN, 10,
                Optional.of(64), 8, false, "");
        var outcome = calculator.win(Seat.EAST, null, true, settings, List.of("基本胡"), 2);
        assertEquals(1200L, outcome.scoreChanges().get(Seat.EAST));
        assertEquals(-400L, outcome.scoreChanges().get(Seat.SOUTH));
        assertEquals(0L, outcome.scoreChanges().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void discardWinChargesOnlySupplierUsingCompleteFormula() {
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.RED_CENTER, 5,
                Optional.of(100), 4, false, "");
        var outcome = calculator.win(Seat.NORTH, Seat.WEST, false, settings, List.of("基本胡"), 3);
        assertEquals(400L, outcome.scoreChanges().get(Seat.NORTH));
        assertEquals(-400L, outcome.scoreChanges().get(Seat.WEST));
        assertEquals(0L, outcome.scoreChanges().get(Seat.EAST));
    }

    @Test
    void capLimitsPatternMultiplierBeforeFinalScoreIsCalculated() {
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.SICHUAN, 3,
                Optional.of(64), 8, false, "");
        var outcome = calculator.win(Seat.SOUTH, Seat.NORTH, false, settings,
                List.of("清十八罗汉"), 9);
        assertEquals(1920L, outcome.scoreChanges().get(Seat.SOUTH));
        assertEquals(-1920L, outcome.scoreChanges().get(Seat.NORTH));
    }
}

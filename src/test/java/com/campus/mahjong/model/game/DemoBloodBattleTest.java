package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.session.DemoSession;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class DemoBloodBattleTest {
    @Test void localWinnerSpectatesBotsAndEntersNextRoundWithoutSettlement() throws Exception {
        DemoSession.createRoom("本机测试", ModeCode.NORTHERN, "2 轮", "2 倍", "128 分");
        MahjongMatch match = new MahjongMatch(new FriendRoomSettings(ModeCode.NORTHERN, 2, Optional.of(128), 2, false, ""), 42, 1);
        var field = DemoSession.class.getDeclaredField("match"); field.setAccessible(true); field.set(null, match);
        try {
            BloodBattleTest.prepareThreeSelfDraws(match.round()); DemoSession.synchronizeProgress();
            DemoSession.declareLocalWin();
            assertFalse(DemoSession.roundFinished());
            assertTrue(DemoSession.winners().contains(Seat.EAST));
            assertTrue(DemoSession.shouldAdvanceSimulation());
            assertTrue(DemoSession.localActions().isEmpty());
            DemoSession.advanceSimulationStep(); DemoSession.advanceSimulationStep();
            assertEquals(2, DemoSession.currentRound());
            assertFalse(DemoSession.roundFinished());
            assertTrue(DemoSession.winners().isEmpty());
            BloodBattleTest.prepareThreeSelfDraws(match.round());
            DemoSession.declareLocalWin(); DemoSession.advanceSimulationStep(); DemoSession.advanceSimulationStep();
            assertTrue(DemoSession.roundFinished());
            assertNotNull(DemoSession.lastResult());
            assertEquals(14, DemoSession.ledger().size());
        } finally { DemoSession.createRoom("清风客", ModeCode.SICHUAN, "8 轮", "2 倍", "128 分"); }
    }
}

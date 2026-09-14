package com.campus.mahjong.integration;

import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.game.MahjongRound;
import com.campus.mahjong.model.game.RoundPhase;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.rule.SettlementCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 根据《测试用例.docx》建立的可追踪验收测试。 */
class DocumentAcceptanceTest {
    private static FriendRoomSettings settings() {
        return new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, "");
    }

    private static MahjongRound openedRound() {
        MahjongRound round = MahjongRound.start(settings(), 20260914L);
        assertTrue(round.expireExchange(round.exchangeDeadline()));
        assertTrue(round.expireMissingSuitSelection(round.missingSuitDeadline()));
        return round;
    }

    @Test
    @DisplayName("TC15/TC16/B06：开局牌数、牌墙余量与当前玩家正确")
    void initialDealAndOpeningStateAreCorrect() {
        MahjongRound round = MahjongRound.start(settings(), 20260914L);

        assertEquals(14, round.hand(Seat.EAST).size());
        assertTrue(round.drawnTile(Seat.EAST).isPresent());
        for (Seat seat : Seat.values()) {
            if (seat != Seat.EAST) {
                assertEquals(13, round.hand(seat).size());
                assertTrue(round.drawnTile(seat).isEmpty());
            }
        }
        assertEquals(55, round.wallRemaining());
        assertEquals(Seat.EAST, round.currentTurn());
        assertEquals(RoundPhase.EXCHANGING_TILES, round.phase());
    }

    @Test
    @DisplayName("TC17/TC18/B07/B08：定缺超时自动完成且不能重复修改")
    void missingSuitTimeoutCompletesOnceAndLocksSelections() {
        MahjongRound round = MahjongRound.start(settings(), 7L);
        round.expireExchange(round.exchangeDeadline());
        long revision = round.revision();
        TileType.Suit choice = round.recommendedMissingSuit(Seat.EAST);

        round.chooseMissingSuit(Seat.EAST, choice, revision);
        assertThrows(IllegalStateException.class,
                () -> round.chooseMissingSuit(Seat.EAST, choice, revision));
        assertTrue(round.expireMissingSuitSelection(round.missingSuitDeadline()));
        assertEquals(4, round.missingSuits().size());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
        assertFalse(round.expireMissingSuitSelection(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("TC21/TC22/TC23/TC24：仅当前玩家可打出手中允许的牌")
    void discardRejectsWrongPlayerAndMissingTile() {
        MahjongRound round = openedRound();
        long revision = round.revision();
        int eastBefore = round.hand(Seat.EAST).size() + (round.drawnTile(Seat.EAST).isPresent() ? 1 : 0);

        assertThrows(IllegalStateException.class,
                () -> round.discard(Seat.SOUTH, round.hand(Seat.SOUTH).getFirst(), false, revision));
        assertThrows(IllegalArgumentException.class,
                () -> round.discard(Seat.EAST, TileType.RED, false, revision));
        assertEquals(eastBefore,
                round.hand(Seat.EAST).size() + (round.drawnTile(Seat.EAST).isPresent() ? 1 : 0));

        TileType allowed = round.discardableTiles(Seat.EAST).getFirst();
        round.discard(Seat.EAST, allowed, round.drawnTile(Seat.EAST).filter(allowed::equals).isPresent(), revision);
        assertEquals(allowed, round.lastDiscard().orElseThrow());
    }

    @Test
    @DisplayName("TC37：系统不提供吃牌动作")
    void chiActionIsAbsent() {
        assertFalse(Arrays.stream(PlayerActionType.values()).anyMatch(type -> type.name().equals("CHI")));
    }

    @Test
    @DisplayName("TC41/B11：旧版本重复请求被拒绝且不会重复记牌")
    void staleDiscardRequestIsIdempotentlyRejected() {
        MahjongRound round = openedRound();
        long revision = round.revision();
        TileType allowed = round.discardableTiles(Seat.EAST).getFirst();
        boolean drawn = round.drawnTile(Seat.EAST).filter(allowed::equals).isPresent();

        round.discard(Seat.EAST, allowed, drawn, revision);
        assertThrows(IllegalStateException.class,
                () -> round.discard(Seat.EAST, allowed, drawn, revision));
        assertEquals(1, round.discards(Seat.EAST).size());
    }

    @Test
    @DisplayName("TC46/TC47/TC49：点炮与自摸结算都保持积分零和")
    void settlementIsZeroSumForDiscardAndSelfDraw() {
        SettlementCalculator calculator = new SettlementCalculator();
        var discardWin = calculator.win(Seat.SOUTH, Seat.EAST, false, settings(), java.util.List.of("平胡"), 0);
        var selfDraw = calculator.win(Seat.SOUTH, Seat.SOUTH, true, settings(), java.util.List.of("平胡"), 0);

        assertEquals(0L, discardWin.scoreChanges().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(0L, selfDraw.scoreChanges().values().stream().mapToLong(Long::longValue).sum());
        assertTrue(discardWin.scoreChanges().get(Seat.SOUTH) > 0);
        assertTrue(selfDraw.scoreChanges().get(Seat.SOUTH) > 0);
    }
}

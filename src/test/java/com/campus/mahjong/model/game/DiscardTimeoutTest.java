package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.campus.mahjong.model.game.TileType.*;

class DiscardTimeoutTest {
    private MahjongRound start(ModeCode mode) {
        var round = MahjongRound.start(new FriendRoomSettings(mode, 2, Optional.of(128), 2, false, ""), 42);
        round.expireExchange(round.exchangeDeadline());
        for (Seat seat : Seat.values()) round.chooseMissingSuit(seat, Suit.MAN, round.revision());
        return round;
    }
    @Test void waitsFifteenSecondsThenDiscardsDrawnTileAndRejectsStaleRequest() throws Exception {
        var round = start(ModeCode.RED_CENTER);
        Map<Seat, Suit> missing = BloodBattleTest.field(round, "missingSuits");
        missing.clear();
        var drawn = round.drawnTile(Seat.EAST).orElseThrow(); long revision = round.revision();
        assertFalse(round.expireDiscard(round.actionStartedAtMillis() + 14999));
        assertTrue(round.expireDiscard(round.actionStartedAtMillis() + 15000));
        assertEquals(List.of(drawn), round.discards(Seat.EAST));
        assertThrows(IllegalStateException.class, () -> round.discard(Seat.EAST, drawn, revision));
        assertFalse(round.expireDiscard(System.currentTimeMillis()));
    }
    @Test void missingSuitOverridesUnrelatedDrawAndRetainsThatDrawInHand() throws Exception {
        var round = start(ModeCode.SICHUAN);
        Map<Seat,List<TileType>> hands = BloodBattleTest.field(round,"hands");
        Map<Seat,TileType> drawn = BloodBattleTest.field(round,"drawnTiles");
        hands.put(Seat.EAST,new ArrayList<>(List.of(PIN_1,MAN_2,SOU_3)));
        drawn.put(Seat.EAST,PIN_9);
        assertTrue(round.expireDiscard(round.actionStartedAtMillis()+15000));
        assertEquals(List.of(MAN_2),round.discards(Seat.EAST));
        assertTrue(round.organizedHand(Seat.EAST).contains(PIN_9));
    }
    @Test void afterPengWithoutDrawAutomaticallyChoosesLegalHandTile() throws Exception {
        var round = start(ModeCode.SICHUAN);
        Map<Seat,List<TileType>> hands = BloodBattleTest.field(round,"hands");
        Map<Seat,TileType> drawn = BloodBattleTest.field(round,"drawnTiles"); drawn.clear();
        hands.put(Seat.EAST,new ArrayList<>(List.of(PIN_1,MAN_2,SOU_3)));
        assertTrue(round.expireDiscard(round.actionStartedAtMillis()+15000));
        assertEquals(List.of(MAN_2),round.discards(Seat.EAST));
    }
    @Test void neitherChoosingNorClaimWindowsAreAutoDiscarded() throws Exception {
        var r = MahjongRound.start(new FriendRoomSettings(ModeCode.SICHUAN,2,Optional.of(128),2,false,""),42);
        assertFalse(r.expireDiscard(Long.MAX_VALUE));
        var phase = MahjongRound.class.getDeclaredField("phase"); phase.setAccessible(true); phase.set(r,RoundPhase.WAITING_FOR_CLAIMS);
        assertFalse(r.expireDiscard(Long.MAX_VALUE));
    }
    @Test void missingSuitIsSortedToRightAndRanksRemainOrdered() throws Exception {
        var r = start(ModeCode.SICHUAN);
        Map<Seat,List<TileType>> hands = BloodBattleTest.field(r,"hands");
        hands.put(Seat.EAST,new ArrayList<>(List.of(MAN_9,SOU_3,MAN_1,PIN_2,MAN_4)));
        assertEquals(List.of(PIN_2,SOU_3,MAN_1,MAN_4,MAN_9),r.organizedHand(Seat.EAST));
    }
    @Test void timeoutAtWallEndAdvancesMatchAndClearsOldDeadline() throws Exception {
        var match = new MahjongMatch(new FriendRoomSettings(ModeCode.SICHUAN,2,Optional.of(128),2,false,""),42,1);
        var r=match.round();r.expireExchange(r.exchangeDeadline());r.expireMissingSuitSelection(r.missingSuitDeadline());
        Deque<TileType> wall=BloodBattleTest.field(r,"wall");wall.clear();
        Map<Seat,List<TileType>> hands=BloodBattleTest.field(r,"hands");
        for(Seat s:List.of(Seat.SOUTH,Seat.WEST,Seat.NORTH))hands.get(s).clear();
        assertTrue(match.expireTimedActions(r.actionStartedAtMillis()+15000));
        assertEquals(2,match.roundNumber());
        assertEquals(RoundPhase.EXCHANGING_TILES,match.round().phase());
        assertFalse(match.expireTimedActions(System.currentTimeMillis()));
    }
}

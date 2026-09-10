package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.campus.mahjong.model.game.TileType.*;

class MissingSuitTest {
    private MahjongRound round() { var r = MahjongRound.start(new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, ""), 42); r.expireExchange(r.exchangeDeadline()); return r; }
    private void chooseAll(MahjongRound r, TileType.Suit suit) {
        r.expireExchange(r.exchangeDeadline());
        for (Seat seat : Seat.values()) r.chooseMissingSuit(seat, suit, r.revision());
    }
    @Test void concurrentChoicesLockAndStartDealerOnlyAfterEveryoneIsReady() {
        var r = round();
        assertEquals(14, r.hand(Seat.EAST).size());
        assertEquals(Set.of(PlayerActionType.DING_QUE), r.legalActions(Seat.EAST));
        assertThrows(IllegalStateException.class, () -> r.discard(Seat.EAST, r.hand(Seat.EAST).getFirst(), r.revision()));
        assertThrows(IllegalArgumentException.class, () -> r.chooseMissingSuit(Seat.EAST, Suit.HONOR, r.revision()));
        r.chooseMissingSuit(Seat.EAST, Suit.MAN, r.revision());
        assertTrue(r.legalActions(Seat.EAST).isEmpty());
        assertThrows(IllegalStateException.class, () -> r.chooseMissingSuit(Seat.EAST, Suit.PIN, r.revision()));
        for (Seat seat : List.of(Seat.SOUTH, Seat.WEST)) r.chooseMissingSuit(seat, Suit.PIN, r.revision());
        assertEquals(RoundPhase.CHOOSING_MISSING_SUIT, r.phase());
        r.chooseMissingSuit(Seat.NORTH, Suit.SOU, r.revision());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, r.phase());
        assertEquals(2, r.revision());
        assertEquals(Seat.EAST, r.currentTurn());
    }
    @Test void deadlinePreservesChoicesAndFillsLeastPopulatedSuitExactlyOnce() {
        var r = round();
        var recommended = new EnumMap<Seat, Suit>(Seat.class);
        for (Seat s : Seat.values()) recommended.put(s, r.recommendedMissingSuit(s));
        r.chooseMissingSuit(Seat.EAST, Suit.SOU, r.revision());
        assertFalse(r.expireMissingSuitSelection(r.missingSuitDeadline() - 1));
        assertTrue(r.expireMissingSuitSelection(r.missingSuitDeadline()));
        assertEquals(Suit.SOU, r.missingSuits().get(Seat.EAST));
        for (Seat s : List.of(Seat.SOUTH, Seat.WEST, Seat.NORTH)) assertEquals(recommended.get(s), r.missingSuits().get(s));
        assertFalse(r.expireMissingSuitSelection(Long.MAX_VALUE));
        assertEquals(2, r.revision());
    }
    @Test void heldOrNewlyDrawnMissingTilesMustBeDiscardedFirst() throws Exception {
        var r = round(); chooseAll(r, Suit.MAN);
        Map<Seat,List<TileType>> hands = BloodBattleTest.field(r, "hands");
        Map<Seat,TileType> drawn = BloodBattleTest.field(r, "drawnTiles");
        hands.put(Seat.EAST, new ArrayList<>(List.of(PIN_1, PIN_2, MAN_1)));
        drawn.put(Seat.EAST, MAN_9);
        assertEquals(Set.of(MAN_1, MAN_9), Set.copyOf(r.discardableTiles(Seat.EAST)));
        assertThrows(IllegalArgumentException.class, () -> r.discard(Seat.EAST, PIN_1, r.revision()));
        hands.get(Seat.EAST).remove(MAN_1);
        assertEquals(List.of(MAN_9), r.discardableTiles(Seat.EAST));
        drawn.remove(Seat.EAST);
        assertEquals(Set.of(PIN_1, PIN_2), Set.copyOf(r.discardableTiles(Seat.EAST)));
    }
    @Test void missingSuitCannotWinOrGangAndCannotClaimMissingDiscard() throws Exception {
        var r = round(); chooseAll(r, Suit.MAN);
        Map<Seat,List<TileType>> hands = BloodBattleTest.field(r, "hands");
        Map<Seat,TileType> drawn = BloodBattleTest.field(r, "drawnTiles"); drawn.clear();
        hands.put(Seat.EAST, new ArrayList<>(List.of(MAN_1,MAN_1,MAN_1,MAN_2,MAN_3,MAN_4,PIN_1,PIN_2,PIN_3,PIN_4,PIN_5,PIN_6,PIN_9,PIN_9)));
        assertFalse(r.legalActions(Seat.EAST).contains(PlayerActionType.HU));
        hands.get(Seat.EAST).add(MAN_1);
        assertFalse(r.selfGangTiles(Seat.EAST).contains(MAN_1));
        assertThrows(IllegalStateException.class, () -> r.declareConcealedGang(Seat.EAST, MAN_1, r.revision()));
        hands.put(Seat.SOUTH, new ArrayList<>(List.of(MAN_1,MAN_1,MAN_1)));
        r.discard(Seat.EAST, MAN_1, r.revision());
        assertFalse(r.legalActions(Seat.SOUTH).contains(PlayerActionType.PENG));
        assertFalse(r.legalActions(Seat.SOUTH).contains(PlayerActionType.GANG));
    }
    @Test void everyNewSichuanRoundRequiresFreshChoices() throws Exception {
        var match = new MahjongMatch(new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, ""), 42, 1);
        var r = match.round(); chooseAll(r, Suit.MAN);
        Deque<TileType> wall = BloodBattleTest.field(r, "wall"); wall.clear();
        for (int i=0; i<4 && r.phase()!=RoundPhase.FINISHED; i++) {
            if(r.phase()==RoundPhase.WAITING_FOR_DISCARD) r.discard(r.currentTurn(), r.discardableTiles(r.currentTurn()).getFirst(), r.revision());
            for(Seat s:Seat.values()) if(r.legalActions(s).contains(PlayerActionType.PASS)) r.submitClaim(s,PlayerActionType.PASS,r.revision());
        }
        match.synchronizeRound();
        assertEquals(2, match.roundNumber());
        assertEquals(RoundPhase.EXCHANGING_TILES, match.round().phase());
        assertTrue(match.round().missingSuits().isEmpty());
    }
}

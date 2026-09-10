package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.campus.mahjong.model.game.TileType.*;

class ExchangeThreeTest {
    private MahjongRound start(long seed) {
        return MahjongRound.start(new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, ""), seed);
    }
    private Map<TileType, Long> counts(List<TileType> tiles) {
        return tiles.stream().collect(java.util.stream.Collectors.groupingBy(t -> t, java.util.stream.Collectors.counting()));
    }
    @Test void invalidSelectionsCannotMutateOrSkipExchange() {
        var r = start(42); var before = r.hand(Seat.EAST);
        assertEquals(Set.of(PlayerActionType.EXCHANGE_THREE), r.legalActions(Seat.EAST));
        assertThrows(IllegalArgumentException.class, () -> r.submitExchange(Seat.EAST, List.of(MAN_1, PIN_1, SOU_1), 0));
        assertThrows(IllegalArgumentException.class, () -> r.submitExchange(Seat.EAST, List.of(MAN_1, MAN_1), 0));
        var absent = Arrays.stream(values()).filter(t -> t.suit() == Suit.MAN && !before.contains(t)).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> r.submitExchange(Seat.EAST, List.of(absent, absent, absent), 0));
        var honor = Arrays.stream(values()).filter(t -> t.suit() == Suit.HONOR).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> r.submitExchange(Seat.EAST, List.of(honor, honor, honor), 0));
        var few = before.stream().filter(t -> Collections.frequency(before, t) < 3).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> r.submitExchange(Seat.EAST, List.of(few, few, few), 0));
        assertThrows(IllegalStateException.class, () -> r.chooseMissingSuit(Seat.EAST, Suit.MAN, 0));
        assertThrows(IllegalStateException.class, () -> r.discard(Seat.EAST, before.getFirst(), 0));
        assertFalse(r.expireDiscard(Long.MAX_VALUE));
        assertFalse(r.expireMissingSuitSelection(Long.MAX_VALUE));
        assertEquals(before, r.hand(Seat.EAST)); assertTrue(r.exchangeReady().isEmpty());
    }
    @Test void allDirectionsTransferOriginalSelectionsAndPreserveEveryTile() {
        var seen = EnumSet.noneOf(ExchangeDirection.class);
        for (long seed = 0; seed < 100; seed++) {
            var r = start(seed);
            var initial = new EnumMap<Seat, List<TileType>>(Seat.class);
            var chosen = new EnumMap<Seat, List<TileType>>(Seat.class);
            for (Seat s : Seat.values()) { initial.put(s, r.hand(s)); chosen.put(s, r.recommendedExchange(s)); }
            int wall = r.wallRemaining();
            for (Seat s : Seat.values()) r.submitExchange(s, chosen.get(s), 0);
            var direction = r.exchangeDirection().orElseThrow(); seen.add(direction);
            for (Seat sender : Seat.values()) {
                var receiver = direction.recipient(sender);
                var expected = new ArrayList<>(initial.get(receiver));
                chosen.get(receiver).forEach(t -> assertTrue(expected.remove(t))); expected.addAll(chosen.get(sender));
                assertEquals(counts(expected), counts(r.hand(receiver)));
                assertEquals(chosen.get(sender), r.exchangeReceived(receiver));
                assertEquals(receiver == Seat.EAST ? 14 : 13, r.hand(receiver).size());
            }
            assertEquals(13, r.organizedHand(Seat.EAST).size()); assertTrue(r.drawnTile(Seat.EAST).isPresent());
            assertEquals(wall, r.wallRemaining()); assertEquals(RoundPhase.CHOOSING_MISSING_SUIT, r.phase());
            assertEquals(1, r.revision());
            assertThrows(IllegalStateException.class, () -> r.submitExchange(Seat.EAST, chosen.get(Seat.EAST), 0));
        }
        assertEquals(EnumSet.allOf(ExchangeDirection.class), seen);
        assertEquals(Seat.NORTH, ExchangeDirection.CLOCKWISE.recipient(Seat.EAST));
        assertEquals(Seat.SOUTH, ExchangeDirection.COUNTERCLOCKWISE.recipient(Seat.EAST));
        assertEquals(Seat.WEST, ExchangeDirection.OPPOSITE.recipient(Seat.EAST));
    }
    @Test void confirmedChoiceIsLockedPrivateAndTimeoutCompletesOnlyOnce() {
        var r = start(42); var initial = r.hand(Seat.EAST);
        var chosen = new ArrayList<>(r.recommendedExchange(Seat.EAST)); var immutable = List.copyOf(chosen);
        r.submitExchange(Seat.EAST, chosen, 0); chosen.clear();
        assertEquals(immutable, r.exchangeSelection(Seat.EAST));
        assertTrue(r.exchangeDirection().isEmpty()); assertTrue(r.exchangeReceived(Seat.EAST).isEmpty());
        assertEquals(initial, r.hand(Seat.EAST)); assertEquals(0, r.revision());
        assertTrue(r.legalActions(Seat.EAST).isEmpty());
        assertThrows(IllegalStateException.class, () -> r.submitExchange(Seat.EAST, immutable, 0));
        assertFalse(r.expireExchange(r.exchangeDeadline() - 1));
        assertTrue(r.expireExchange(r.exchangeDeadline()));
        assertEquals(immutable, r.exchangeSelection(Seat.EAST)); assertEquals(4, r.exchangeReady().size());
        assertTrue(r.missingSuitDeadline() > System.currentTimeMillis() + 9000);
        assertFalse(r.expireExchange(Long.MAX_VALUE));
        assertEquals(RoundPhase.CHOOSING_MISSING_SUIT, r.phase());
    }
    @Test void dealerCanExchangeUniqueFourteenthTile() throws Exception {
        var r = start(42);
        Map<Seat, List<TileType>> hands = BloodBattleTest.field(r, "hands");
        Map<Seat, TileType> drawn = BloodBattleTest.field(r, "drawnTiles");
        hands.put(Seat.EAST, new ArrayList<>(List.of(MAN_1, MAN_2, PIN_1, PIN_2, PIN_3, PIN_4, PIN_5, PIN_6, PIN_7, PIN_8, PIN_9, SOU_1, SOU_2)));
        drawn.put(Seat.EAST, MAN_9);
        r.submitExchange(Seat.EAST, List.of(MAN_1, MAN_2, MAN_9), 0);
        r.expireExchange(r.exchangeDeadline());
        assertEquals(14, r.hand(Seat.EAST).size()); assertEquals(13, r.organizedHand(Seat.EAST).size());
        assertTrue(r.exchangeReceived(Seat.EAST).contains(r.drawnTile(Seat.EAST).orElseThrow()));
    }
    @Test void matchTimeoutStartsFreshMissingSuitWindowAndOtherModesSkipExchange() {
        var match = new MahjongMatch(new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, ""), 42, 1);
        assertTrue(match.expireTimedActions(match.round().exchangeDeadline()));
        assertEquals(RoundPhase.CHOOSING_MISSING_SUIT, match.round().phase());
        assertTrue(match.round().missingSuits().isEmpty());
        for (ModeCode mode : List.of(ModeCode.NORTHERN, ModeCode.CHANGSHA, ModeCode.RED_CENTER)) {
            var r = MahjongRound.start(new FriendRoomSettings(mode, 2, Optional.of(128), 2, false, ""), 42);
            assertEquals(RoundPhase.WAITING_FOR_DISCARD, r.phase());
        }
    }
}

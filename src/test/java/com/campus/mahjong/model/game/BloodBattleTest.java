package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.campus.mahjong.model.game.TileType.*;
import static org.junit.jupiter.api.Assertions.*;

class BloodBattleTest {
    private FriendRoomSettings settings(int rounds) {
        return new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), rounds, false, "");
    }

    @Test void threeSelfDrawWinnersContinueAndPreviousWinnersStopPaying() throws Exception {
        MahjongRound round = MahjongRound.start(settings(1), 42);
        prepareThreeSelfDraws(round);
        round.declareSelfDraw(Seat.EAST, round.revision());
        assertEquals(Set.of(Seat.EAST), round.winners());
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(EAST, round.winDetails().getFirst().tile());
        assertTrue(round.winDetails().getFirst().selfDraw());
        assertEquals(round.wins().getFirst().scoreChanges(), round.winDetails().getFirst().scoreChanges());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
        assertTrue(round.legalActions(Seat.EAST).isEmpty());
        assertThrows(IllegalStateException.class, () -> round.declareSelfDraw(Seat.EAST, round.revision()));
        round.declareSelfDraw(Seat.SOUTH, round.revision());
        assertEquals(0, round.wins().get(1).scoreChanges().get(Seat.EAST));
        assertEquals(Seat.WEST, round.currentTurn());
        round.declareSelfDraw(Seat.WEST, round.revision());
        assertEquals(RoundPhase.FINISHED, round.phase());
        assertEquals(3, round.winners().size());
        assertEquals(0, round.wins().get(2).scoreChanges().get(Seat.EAST));
        assertEquals(0, round.wins().get(2).scoreChanges().get(Seat.SOUTH));
        assertTrue(round.wins().get(2).scoreChanges().get(Seat.NORTH) < 0);
        assertEquals(0, round.outcome().orElseThrow().scoreChanges().values().stream().mapToLong(Long::longValue).sum());
    }

    @Test void oneDiscardCanProduceThreeWinnersAndEachGetsALedgerEntry() throws Exception {
        MahjongMatch match = new MahjongMatch(settings(1), 42, 1);
        prepare(match.round(), Map.of(Seat.SOUTH, ready(MAN_1, RED), Seat.WEST, ready(PIN_1, RED), Seat.NORTH, ready(SOU_1, RED)), RED, List.of());
        match.apply(Seat.EAST, PlayerActionType.DISCARD, RED, match.revision());
        long revision = match.revision();
        match.apply(Seat.NORTH, PlayerActionType.HU, null, revision);
        match.apply(Seat.WEST, PlayerActionType.HU, null, revision);
        assertFalse(match.finished());
        match.apply(Seat.SOUTH, PlayerActionType.HU, null, revision);
        assertTrue(match.finished());
        assertEquals(3, match.round().wins().size());
        assertEquals(3, match.winEvents().size());
        assertTrue(match.winEvents().stream().allMatch(e -> e.tile() == RED && !e.selfDraw()));
        assertEquals(List.of(1, 2, 3), match.winEvents().stream().map(WinEvent::sequence).toList());
        assertEquals(4, match.ledger().size());
        assertEquals(3, match.ledger().stream().filter(entry -> entry.payer().filter(Seat.EAST::equals).isPresent()).count());
        assertLedgerBalances(match);
    }

    @Test void twoWinnersAreSkippedWhenDrawingNextTile() throws Exception {
        MahjongRound round = MahjongRound.start(settings(1), 42);
        prepare(round, Map.of(Seat.SOUTH, ready(MAN_1, RED), Seat.WEST, ready(PIN_1, RED)), RED, List.of());
        round.discard(Seat.EAST, RED, round.revision());
        long revision = round.revision();
        round.submitClaim(Seat.SOUTH, PlayerActionType.HU, revision);
        round.submitClaim(Seat.WEST, PlayerActionType.HU, revision);
        assertEquals(Seat.NORTH, round.currentTurn());
        assertEquals(14, round.hand(Seat.NORTH).size());
        assertTrue(round.legalActions(Seat.SOUTH).isEmpty());
        assertTrue(round.legalActions(Seat.WEST).isEmpty());
        assertEquals(13, round.hand(Seat.SOUTH).size());
    }

    @Test void roundsAdvanceAutomaticallyAndScoresLedgerAndRevisionSurvive() throws Exception {
        MahjongMatch match = new MahjongMatch(settings(2), 42, 1);
        prepareThreeSelfDraws(match.round());
        for (Seat seat : List.of(Seat.EAST, Seat.SOUTH, Seat.WEST)) match.apply(seat, PlayerActionType.HU, null, match.revision());
        assertEquals(2, match.roundNumber());
        assertFalse(match.finished());
        assertTrue(match.round().winners().isEmpty());
        assertEquals(55, match.round().wallRemaining());
        Map<Seat, Long> firstRound = match.scores();
        long secondRevision = match.revision();
        assertTrue(secondRevision > 0);
        assertThrows(IllegalStateException.class, () -> match.apply(Seat.EAST, PlayerActionType.DISCARD, match.round().hand(Seat.EAST).getFirst(), 0));
        prepareThreeSelfDraws(match.round());
        for (Seat seat : List.of(Seat.EAST, Seat.SOUTH, Seat.WEST)) match.apply(seat, PlayerActionType.HU, null, match.revision());
        assertTrue(match.finished());
        assertEquals(2, match.roundNumber());
        assertEquals(14, match.ledger().size());
        for (Seat seat : Seat.values()) assertEquals(firstRound.get(seat) * 2, match.scores().get(seat));
        match.synchronizeRound(); match.synchronizeRound();
        assertEquals(6, match.winEvents().size());
        assertEquals(List.of(1, 1, 1, 2, 2, 2), match.winEvents().stream().map(WinEvent::round).toList());
        assertEquals(14, match.ledger().size());
        assertThrows(IllegalStateException.class, () -> match.apply(Seat.NORTH, PlayerActionType.PASS, null, match.revision()));
        assertLedgerBalances(match);
    }

    @Test void exhaustedWallEndsRoundAndPreservesEarlierWin() throws Exception {
        MahjongMatch match = new MahjongMatch(settings(1), 42, 1);
        prepareThreeSelfDraws(match.round());
        match.apply(Seat.EAST, PlayerActionType.HU, null, match.revision());
        Map<Seat, Long> scores = match.scores();
        Deque<TileType> wall = field(match.round(), "wall"); wall.clear();
        match.apply(Seat.SOUTH, PlayerActionType.DISCARD, SOUTH, match.revision());
        assertTrue(match.finished());
        assertEquals(scores, match.scores());
        assertEquals("牌墙耗尽", match.ledger().getLast().reason());
        assertEquals(4, match.ledger().size());
        assertLedgerBalances(match);
    }

    @Test void noWinnerDrawAdvancesToNextRoundWithoutInventingScores() throws Exception {
        MahjongMatch match = new MahjongMatch(settings(2), 42, 1);
        prepare(match.round(), Map.of(), RED, List.of());
        Deque<TileType> wall = field(match.round(), "wall"); wall.clear();
        match.apply(Seat.EAST, PlayerActionType.DISCARD, RED, match.revision());
        assertEquals(2, match.roundNumber());
        assertEquals(1, match.ledger().size());
        assertTrue(match.scores().values().stream().allMatch(score -> score == 0));
    }

    static void prepareThreeSelfDraws(MahjongRound round) throws Exception {
        prepare(round, Map.of(Seat.EAST, ready(MAN_1, EAST), Seat.SOUTH, ready(PIN_1, SOUTH), Seat.WEST, ready(SOU_1, WEST)), EAST, List.of(SOUTH, WEST));
    }

    static List<TileType> ready(TileType suitStart, TileType pair) {
        List<TileType> hand = new ArrayList<>();
        for (int i = 0; i < 4; i++) hand.addAll(Collections.nCopies(3, TileType.values()[suitStart.ordinal() + i]));
        hand.add(pair); return hand;
    }

    static void prepare(MahjongRound round, Map<Seat, List<TileType>> overrides, TileType drawn, List<TileType> next) throws Exception {
        if (round.phase() == RoundPhase.EXCHANGING_TILES) round.expireExchange(round.exchangeDeadline());
        if (round.phase() == RoundPhase.CHOOSING_MISSING_SUIT) round.expireMissingSuitSelection(round.missingSuitDeadline());
        Map<Seat, TileType.Suit> missing = field(round, "missingSuits"); missing.clear();
        List<TileType> filler = List.of(MAN_5, MAN_7, MAN_9, PIN_5, PIN_7, PIN_9, SOU_5, SOU_7, SOU_9, GREEN, WHITE, NORTH, MAN_6);
        Map<Seat, List<TileType>> hands = field(round, "hands");
        Map<Seat, TileType> draws = field(round, "drawnTiles"); draws.clear(); draws.put(Seat.EAST, drawn);
        List<TileType> remaining = new ArrayList<>(TileType.fullWall());
        for (Seat seat : Seat.values()) {
            hands.put(seat, new ArrayList<>(overrides.getOrDefault(seat, filler)));
            for (TileType tile : round.hand(seat)) assertTrue(remaining.remove(tile), "extra tile " + tile);
        }
        Deque<TileType> wall = field(round, "wall"); wall.clear();
        for (TileType tile : next) { assertTrue(remaining.remove(tile)); wall.add(tile); }
        wall.addAll(remaining);
    }

    private void assertLedgerBalances(MahjongMatch match) {
        EnumMap<Seat, Long> totals = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) totals.put(seat, 0L);
        for (ScoreEntry entry : match.ledger()) {
            entry.payer().ifPresent(seat -> totals.merge(seat, -entry.amount(), Long::sum));
            entry.payee().ifPresent(seat -> totals.merge(seat, entry.amount(), Long::sum));
        }
        assertEquals(match.scores(), totals);
        assertEquals(0, totals.values().stream().mapToLong(Long::longValue).sum());
    }

    @SuppressWarnings("unchecked") static <T> T field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T)field.get(object);
    }
}

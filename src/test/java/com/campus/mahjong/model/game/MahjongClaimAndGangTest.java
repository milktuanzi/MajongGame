package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.campus.mahjong.model.game.TileType.*;
import static org.junit.jupiter.api.Assertions.*;

class MahjongClaimAndGangTest {
    private static final List<TileType> FILLER = List.of(MAN_1, MAN_3, MAN_5, MAN_7, MAN_9,
            PIN_1, PIN_3, PIN_5, PIN_7, PIN_9, SOU_1, SOU_5, SOU_9);

    @Test void noClaimAutomaticallyDrawsForNextPlayerAndRejectsStaleAction() throws Exception {
        MahjongRound round = fixture(Map.of(), RED, List.of());
        round.discard(Seat.EAST, RED, 0);
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(14, round.hand(Seat.SOUTH).size());
        assertEquals(82, round.wallRemaining());
        assertTrue(round.drawnTile(Seat.SOUTH).isPresent());
        assertTrue(round.legalActions(Seat.WEST).isEmpty());
        assertThrows(IllegalStateException.class, () -> round.discard(Seat.EAST, RED, 0));
        assertConservation(round);
    }

    @Test void onlyEligiblePlayerRespondsAndPassAdvancesImmediately() throws Exception {
        MahjongRound round = fixture(Map.of(Seat.WEST, withCopies(RED, 2, 13)), RED, List.of());
        round.discard(Seat.EAST, RED, 0);
        assertEquals(EnumSet.of(PlayerActionType.PENG, PlayerActionType.PASS), round.legalActions(Seat.WEST));
        assertTrue(round.legalActions(Seat.SOUTH).isEmpty());
        assertTrue(round.legalActions(Seat.NORTH).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> round.submitClaim(Seat.SOUTH, PlayerActionType.PASS, round.revision()));
        round.submitClaim(Seat.WEST, PlayerActionType.PASS, round.revision());
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
        assertConservation(round);
    }

    @Test void pengConsumesTwoTilesAndDoesNotDraw() throws Exception {
        MahjongRound round = fixture(Map.of(Seat.WEST, withCopies(RED, 2, 13)), RED, List.of());
        round.discard(Seat.EAST, RED, 0);
        round.submitClaim(Seat.WEST, PlayerActionType.PENG, round.revision());
        assertEquals(Seat.WEST, round.currentTurn());
        assertEquals(11, round.hand(Seat.WEST).size());
        assertEquals(83, round.wallRemaining());
        assertTrue(round.drawnTile(Seat.WEST).isEmpty());
        assertTrue(round.discards(Seat.EAST).isEmpty());
        assertEquals(new Meld(PlayerActionType.PENG, List.of(RED, RED, RED), Seat.EAST), round.melds(Seat.WEST).getFirst());
        assertConservation(round);
    }

    @Test void exposedGangConsumesThreeAndDrawsReplacement() throws Exception {
        MahjongRound round = fixture(Map.of(Seat.WEST, withCopies(RED, 3, 13)), RED, List.of());
        round.discard(Seat.EAST, RED, 0);
        assertTrue(round.legalActions(Seat.WEST).contains(PlayerActionType.GANG));
        round.submitClaim(Seat.WEST, PlayerActionType.GANG, round.revision());
        assertEquals(Seat.WEST, round.currentTurn());
        assertEquals(11, round.hand(Seat.WEST).size());
        assertEquals(82, round.wallRemaining());
        assertTrue(round.drawnTile(Seat.WEST).isPresent());
        assertEquals(4, round.melds(Seat.WEST).getFirst().tiles().size());
        assertTrue(round.discards(Seat.EAST).isEmpty());
        assertConservation(round);
    }

    @Test void concealedGangWorksWithFourthTileDrawnOrAlreadyInHand() throws Exception {
        for (boolean fourthDrawn : List.of(true, false)) {
            MahjongRound round = fixture(Map.of(Seat.EAST, withCopies(RED, fourthDrawn ? 3 : 4, 13)),
                    fourthDrawn ? RED : GREEN, List.of());
            assertTrue(round.selfGangTiles(Seat.EAST).contains(RED));
            round.declareSelfGang(Seat.EAST, RED, 0);
            assertEquals(11, round.hand(Seat.EAST).size());
            assertEquals(82, round.wallRemaining());
            assertEquals(Seat.EAST, round.melds(Seat.EAST).getFirst().fromSeat());
            assertTrue(round.drawnTile(Seat.EAST).isPresent());
            if (!fourthDrawn) assertTrue(round.organizedHand(Seat.EAST).contains(GREEN));
            assertConservation(round);
        }
    }

    @Test void supplementalGangUpgradesPengAndPreservesUnrelatedDraw() throws Exception {
        for (boolean fourthDrawn : List.of(true, false)) {
            MahjongRound round = fixture(Map.of(Seat.EAST, withCopies(RED, fourthDrawn ? 0 : 1, 10)),
                    fourthDrawn ? RED : GREEN,
                    List.of(new Meld(PlayerActionType.PENG, List.of(RED, RED, RED), Seat.NORTH)));
            round.declareSelfGang(Seat.EAST, RED, 0);
            assertEquals(1, round.melds(Seat.EAST).size());
            assertEquals(new Meld(PlayerActionType.GANG, List.of(RED, RED, RED, RED), Seat.NORTH), round.melds(Seat.EAST).getFirst());
            assertEquals(11, round.hand(Seat.EAST).size());
            assertEquals(82, round.wallRemaining());
            if (!fourthDrawn) assertTrue(round.organizedHand(Seat.EAST).contains(GREEN));
            assertConservation(round);
        }
    }

    @Test void huTakesPriorityOverPengRegardlessOfResponseOrder() throws Exception {
        List<TileType> ready = List.of(MAN_2, MAN_2, MAN_2, MAN_4, MAN_4, MAN_4,
                PIN_2, PIN_2, PIN_2, SOU_2, SOU_2, SOU_2, RED);
        for (boolean huFirst : List.of(true, false)) {
            MahjongRound round = fixture(Map.of(Seat.SOUTH, withCopies(RED, 2, 13), Seat.WEST, ready), RED, List.of());
            round.discard(Seat.EAST, RED, 0);
            long revision = round.revision();
            assertTrue(round.legalActions(Seat.NORTH).isEmpty());
            round.submitClaim(huFirst ? Seat.WEST : Seat.SOUTH, huFirst ? PlayerActionType.HU : PlayerActionType.PENG, revision);
            assertEquals(RoundPhase.WAITING_FOR_CLAIMS, round.phase());
            round.submitClaim(huFirst ? Seat.SOUTH : Seat.WEST, huFirst ? PlayerActionType.PENG : PlayerActionType.HU, revision);
            assertEquals(Seat.WEST, round.wins().getFirst().winner().orElseThrow());
            assertFalse(round.wins().getFirst().selfDraw());
            assertTrue(round.melds(Seat.SOUTH).isEmpty());
        }
    }

    @Test void chiOnlyPromptsTheNextSeatInNorthernRules() throws Exception {
        MahjongRound round = fixture(Map.of(), MAN_2, List.of());
        round.discard(Seat.EAST, MAN_2, 0);
        assertEquals(EnumSet.of(PlayerActionType.CHI, PlayerActionType.PASS), round.legalActions(Seat.SOUTH));
        assertTrue(round.legalActions(Seat.WEST).isEmpty());
        round.submitClaim(Seat.SOUTH, PlayerActionType.CHI, round.revision());
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(11, round.hand(Seat.SOUTH).size());
        assertTrue(round.drawnTile(Seat.SOUTH).isEmpty());
        assertConservation(round);
    }

    @Test void invalidGangDoesNotMutateRound() throws Exception {
        MahjongRound round = fixture(Map.of(), RED, List.of());
        List<TileType> before = round.hand(Seat.EAST);
        assertThrows(IllegalStateException.class, () -> round.declareSelfGang(Seat.EAST, RED, 0));
        assertThrows(IllegalStateException.class, () -> round.declareSupplementalGang(Seat.EAST, RED, 0));
        assertThrows(IllegalStateException.class, () -> round.declareConcealedGang(Seat.SOUTH, RED, 0));
        assertEquals(before, round.hand(Seat.EAST));
        assertEquals(0, round.revision());
        assertConservation(round);
    }

    private List<TileType> withCopies(TileType tile, int copies, int size) {
        List<TileType> hand = new ArrayList<>(Collections.nCopies(copies, tile));
        hand.addAll(FILLER.subList(0, size - copies));
        return hand;
    }

    // 固定合法牌局，避免随机发牌让关键回归分支时有时无；不增加生产环境的改牌入口。
    private MahjongRound fixture(Map<Seat, List<TileType>> overrides, TileType drawn, List<Meld> eastMelds) throws Exception {
        MahjongRound round = MahjongRound.start(new FriendRoomSettings(ModeCode.NORTHERN, 2, Optional.of(128), 8, false, ""), 42);
        Map<Seat, List<TileType>> hands = field(round, "hands");
        for (Seat seat : Seat.values()) hands.put(seat, new ArrayList<>(overrides.getOrDefault(seat, FILLER)));
        Map<Seat, TileType> draws = field(round, "drawnTiles");
        draws.clear(); draws.put(Seat.EAST, drawn);
        Map<Seat, List<Meld>> melds = field(round, "melds");
        melds.put(Seat.EAST, new ArrayList<>(eastMelds));
        List<TileType> remaining = new ArrayList<>(TileType.fullWall());
        for (Seat seat : Seat.values()) {
            for (TileType tile : round.hand(seat)) assertTrue(remaining.remove(tile));
            for (Meld meld : round.melds(seat)) for (TileType tile : meld.tiles()) assertTrue(remaining.remove(tile));
        }
        Deque<TileType> wall = field(round, "wall");
        wall.clear(); wall.addAll(remaining);
        return round;
    }

    private void assertConservation(MahjongRound round) throws Exception {
        Deque<TileType> wall = field(round, "wall");
        List<TileType> tiles = new ArrayList<>(wall);
        for (Seat seat : Seat.values()) {
            tiles.addAll(round.hand(seat)); tiles.addAll(round.discards(seat));
            round.melds(seat).forEach(meld -> tiles.addAll(meld.tiles()));
        }
        assertEquals(136, tiles.size());
        for (TileType tile : TileType.values()) assertEquals(4, Collections.frequency(tiles, tile));
    }

    @SuppressWarnings("unchecked")
    private <T> T field(MahjongRound round, String name) throws Exception {
        var field = MahjongRound.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(round);
    }
}

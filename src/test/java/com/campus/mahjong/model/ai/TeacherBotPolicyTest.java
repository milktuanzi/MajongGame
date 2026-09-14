package com.campus.mahjong.model.ai;

import com.campus.mahjong.infrastructure.game.InMemoryGameSessionService;
import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TeacherBotPolicyTest {
    static RoomSnapshot room(ModeCode mode) {
        return new RoomSnapshot(new RoomId("teacher-test"), RoomKind.FRIEND, mode, RoomStatus.WAITING,
                Arrays.stream(Seat.values()).map(s -> new RoomPlayer(new PlayerProfile(new PlayerId(s.name()), s.name(), "", 0, 1), s, s == Seat.EAST, true, true, s != Seat.EAST)).toList(),
                4, Optional.of(new FriendRoomSettings(mode, 1, Optional.of(128), 2, false, "")), 0);
    }
    @Test void retrievalFiltersModeAndDistinguishesRulesFromAdvice() {
        var kb = new RuleKnowledgeBase();
        var red = kb.retrieve(ModeCode.RED_CENTER, Set.of("DISCARD"));
        assertTrue(red.stream().anyMatch(c -> c.id().equals("RED-WILDCARD")));
        var sichuan = kb.retrieve(ModeCode.SICHUAN, Set.of("DISCARD"));
        assertTrue(sichuan.stream().noneMatch(c -> c.id().equals("RED-WILDCARD")));
        assertEquals("HARD", sichuan.getFirst().kind());
        assertTrue(sichuan.stream().anyMatch(c -> c.kind().equals("STRATEGY")));
        assertThrows(IllegalArgumentException.class, () -> kb.retrieve(ModeCode.SICHUAN, Set.of("UNKNOWN")));
        assertTrue(kb.retrieve(ModeCode.RED_CENTER, Set.of("SCORE")).getFirst().text().contains("10 ×"));
        assertTrue(kb.retrieve(ModeCode.RED_CENTER, Set.of("EXCHANGE")).getFirst().text().contains("四川与红中"));
    }
    @Test void mockOutputUsesOnlyRetrievedIdsAndCarriesDecisionIdentity() {
        var citations = new RuleKnowledgeBase().retrieve(ModeCode.SICHUAN, Set.of("DISCARD"));
        var request = new TeacherExplanationProvider.Request("lesson-1", RuleKnowledgeBase.VERSION, "SICHUAN", "DISCARD", "一万", "先打出缺门牌", citations);
        var provider = new MockTeacherExplanationProvider();
        var response = provider.explain(request).toCompletableFuture().join();
        assertEquals(response, provider.explain(request).toCompletableFuture().join());
        assertEquals("lesson-1", response.lessonId());
        assertEquals("MOCK", response.provider());
        assertTrue(response.explanation().contains("先打出缺门牌"));
        assertEquals(citations.stream().map(RuleKnowledgeBase.Citation::id).toList(), response.citedRuleIds());
        assertThrows(java.util.concurrent.CompletionException.class, () -> provider.explain(new TeacherExplanationProvider.Request("x","v","SICHUAN","DISCARD","一万","",List.of())).toCompletableFuture().join());
    }
    @Test void allPhasesAndMultipleRoundsUseOnlyAcceptedActionsWithFixedSeed() {
        for (ModeCode mode : ModeCode.values()) {
            var first = new InMemoryGameSessionService();
            var second = new InMemoryGameSessionService();
            var id = first.create(room(mode), 118, 1);
            var id2 = second.create(room(mode), 118, 1);
            var policy = new TeacherBotPolicy(new RuleKnowledgeBase());
            int discards = 0, moves = 0;
            for (int step = 0; step < 700; step++) {
                boolean changed = false;
                for (Seat seat : Seat.values()) {
                    PlayerId player = new PlayerId(seat.name());
                    var state = first.snapshot(id, player).toCompletableFuture().join();
                    var state2 = second.snapshot(id2, player).toCompletableFuture().join();
                    var choice = policy.choose(mode, seat, state);
                    assertEquals(choice, policy.choose(mode, seat, state2));
                    if (choice.isEmpty()) continue;
                    var decision = choice.orElseThrow();
                    assertFalse(decision.citations().isEmpty());
                    assertTrue(state.availableActions().stream().anyMatch(a -> a.type() == decision.type()));
                    if (decision.type() == PlayerActionType.DISCARD) {
                        discards++;
                        assertTrue(state.availableActions().stream().filter(a -> a.type() == decision.type()).findFirst().orElseThrow().relatedTiles().containsAll(decision.tiles()));
                    }
                    if (decision.type() == PlayerActionType.EXCHANGE_THREE) assertEquals(3, decision.tiles().size());
                    assertTrue(first.perform(decision.request(state, player)).toCompletableFuture().join().accepted());
                    assertTrue(second.perform(decision.request(state2, player)).toCompletableFuture().join().accepted());
                    changed = true; moves++;
                }
                if (!changed) break;
            }
            var end = first.snapshot(id, new PlayerId("EAST")).toCompletableFuture().join();
            assertEquals(GameStatus.FINISHED, end.status(), "机器人不能卡住牌局");
            assertEquals(2, end.currentRound());
            assertTrue(discards > 10); assertTrue(moves > discards);
            assertEquals(0L, first.latestSettlement(id).toCompletableFuture().join().changes().stream().mapToLong(ScoreChange::delta).sum());
        }
    }
    @Test void redWildcardGetsRetentionWeightWithoutBecomingAnIllegalDiscardRule() {
        var hand = List.of(com.campus.mahjong.model.game.TileType.RED, com.campus.mahjong.model.game.TileType.MAN_1);
        assertTrue(TeacherBotPolicy.keepValue(hand, hand.getFirst(), ModeCode.RED_CENTER) > TeacherBotPolicy.keepValue(hand, hand.getLast(), ModeCode.RED_CENTER));
    }
}

package com.campus.mahjong.integration;

import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanConnectivityTest {
    @Test
    void fourClientsJoinReadyStartAndShareAnAuthoritativeRound() throws Exception {
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.NORTHERN,
                2, Optional.of(128), 2, false, "");
        LanSession host = await(LanSession.host(player("房主"), settings, 0, "127.0.0.1"));
        List<LanSession> sessions = new ArrayList<>();
        sessions.add(host);
        try {
            sessions.add(await(LanSession.join(player("南家"), host.invitation())));
            sessions.add(await(LanSession.join(player("西家"), host.invitation())));
            sessions.add(await(LanSession.join(player("北家"), host.invitation())));
            waitUntil(() -> host.currentRoom().orElseThrow().players().size() == 4);

            for (LanSession session : sessions.stream().filter(item -> !item.owner()).toList()) {
                RoomSnapshot room = session.currentRoom().orElseThrow();
                await(session.setReady(room.roomId(), session.localPlayer().id(), true));
            }
            waitUntil(() -> allReady(host.currentRoom().orElseThrow()));

            RoomSnapshot room = host.currentRoom().orElseThrow();
            GameId gameId = await(host.start(room.roomId(), host.localPlayer().id()));
            waitUntil(() -> sessions.stream().allMatch(session -> session.currentGame().isPresent()));

            assertTrue(sessions.stream().map(session -> session.currentGame().orElseThrow().gameId())
                    .allMatch(gameId::equals));
            long started = host.currentGame().orElseThrow().actionStartedAtMillis();
            assertTrue(started > 0);
            assertTrue(sessions.stream().allMatch(session -> session.currentGame().orElseThrow().actionStartedAtMillis() == started));
            assertTrue(sessions.stream().noneMatch(session -> session.currentGame().orElseThrow().waitingForClaims()));
            assertEquals(4, host.currentGame().orElseThrow().players().size());
            assertEquals(13, host.currentGame().orElseThrow().ownHand().size());
            assertTrue(host.currentGame().orElseThrow().drawnTile().isPresent());
            for (LanSession guest : sessions.stream().filter(item -> !item.owner()).toList()) {
                assertEquals(13, guest.currentGame().orElseThrow().ownHand().size());
                assertFalse(guest.currentGame().orElseThrow().drawnTile().isPresent());
            }

            Tile discard = host.currentGame().orElseThrow().drawnTile().orElseThrow();
            assertTrue(await(host.perform(PlayerActionType.DISCARD, List.of(discard))).accepted());
            waitUntil(() -> sessions.stream().allMatch(session ->
                    session.currentGame().orElseThrow().revision() >= 1));

            boolean waiting = host.currentGame().orElseThrow().waitingForClaims();
            assertTrue(sessions.stream().allMatch(session -> session.currentGame().orElseThrow().waitingForClaims() == waiting));
            List<LanSession> responders = sessions.stream()
                    .filter(session -> session.currentGame().orElseThrow().availableActions().stream()
                            .anyMatch(option -> option.type() == PlayerActionType.PASS))
                    .sorted(Comparator.comparing(session -> localSeat(session).ordinal())).toList();
            for (LanSession responder : responders) {
                assertTrue(await(responder.perform(PlayerActionType.PASS, List.of())).accepted());
            }
            waitUntil(() -> sessions.stream().allMatch(session ->
                    session.currentGame().orElseThrow().revision() == 2));
            assertEquals(Seat.SOUTH, host.currentGame().orElseThrow().currentTurn());
            int steps = 0;
            while (host.currentGame().orElseThrow().status() != GameStatus.FINISHED && steps++ < 1000) {
                long previous = host.currentGame().orElseThrow().revision();
                List<LanSession> claimants = sessions.stream().filter(session -> session.currentGame().orElseThrow()
                        .availableActions().stream().anyMatch(action -> action.type() == PlayerActionType.PASS)).toList();
                if (!claimants.isEmpty()) {
                    for (LanSession claimant : claimants) {
                        boolean hu = claimant.currentGame().orElseThrow().availableActions().stream().anyMatch(action -> action.type() == PlayerActionType.HU);
                        assertTrue(await(claimant.perform(hu ? PlayerActionType.HU : PlayerActionType.PASS, List.of())).accepted());
                    }
                } else {
                    LanSession active = sessions.stream().filter(session -> session.currentGame().orElseThrow()
                            .availableActions().stream().anyMatch(action -> action.type() == PlayerActionType.DISCARD)).findFirst().orElseThrow();
                    GameSnapshot state = active.currentGame().orElseThrow();
                    boolean hu = state.availableActions().stream().anyMatch(action -> action.type() == PlayerActionType.HU);
                    assertTrue(await(active.perform(hu ? PlayerActionType.HU : PlayerActionType.DISCARD,
                            hu ? List.of() : List.of(state.drawnTile().orElseGet(() -> state.ownHand().getFirst())))).accepted());
                }
                waitUntil(() -> sessions.stream().allMatch(session -> session.currentGame().orElseThrow().revision() > previous));
                GameSnapshot authoritative = host.currentGame().orElseThrow();
                assertTrue(sessions.stream().allMatch(session -> session.currentGame().orElseThrow().currentRound() == authoritative.currentRound()));
                assertTrue(sessions.stream().allMatch(session -> session.currentGame().orElseThrow().ledger().equals(authoritative.ledger())));
                assertTrue(sessions.stream().allMatch(session -> session.currentGame().orElseThrow().winners().equals(authoritative.winners())));
            }
            assertEquals(GameStatus.FINISHED, host.currentGame().orElseThrow().status());
            assertEquals(2, host.currentGame().orElseThrow().currentRound());
            assertEquals(2, host.currentGame().orElseThrow().ledger().stream().filter(entry -> entry.payer().isEmpty()).count());
            for (LanSession session : sessions) assertEquals(2, await(session.latestSettlement(gameId)).round());
        } finally {
            sessions.reversed().forEach(LanSession::close);
        }
    }

    @Test
    void sichuanChoicesArePrivateAndServerCompletesTimeoutWithoutClientActions() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, "");
        List<LanSession> sessions = new ArrayList<>();
        LanSession host = await(LanSession.host(player("定缺房主"), settings, 0, "127.0.0.1"));
        sessions.add(host);
        try {
            for (int i = 0; i < 3; i++) sessions.add(await(LanSession.join(player("定缺玩家" + i), host.invitation())));
            for (LanSession guest : sessions.subList(1, 4))
                await(guest.setReady(guest.currentRoom().orElseThrow().roomId(), guest.localPlayer().id(), true));
            waitUntil(() -> allReady(host.currentRoom().orElseThrow()));
            await(host.start(host.currentRoom().orElseThrow().roomId(), host.localPlayer().id()));
            waitUntil(() -> sessions.stream().allMatch(s -> s.currentGame().isPresent()));
            long deadline = host.currentGame().orElseThrow().missingSuitDeadline();
            assertTrue(deadline > System.currentTimeMillis());
            assertTrue(sessions.stream().allMatch(s -> s.currentGame().orElseThrow().choosingMissingSuit()));
            assertFalse(await(host.perform(PlayerActionType.DISCARD, List.of(host.currentGame().orElseThrow().ownHand().getFirst()))).accepted());
            assertTrue(await(host.perform(PlayerActionType.DING_QUE, List.of(new Tile("一万")))).accepted());
            waitUntil(() -> sessions.stream().allMatch(s -> s.currentGame().orElseThrow().missingSuitReady().contains(Seat.EAST)));
            assertEquals(1, host.currentGame().orElseThrow().missingSuits().size());
            for (LanSession guest : sessions.subList(1, 4)) assertTrue(guest.currentGame().orElseThrow().missingSuits().isEmpty());
            assertFalse(await(host.perform(PlayerActionType.DING_QUE, List.of(new Tile("一筒")))).accepted());
            long timeout = System.nanoTime() + Duration.ofSeconds(12).toNanos();
            while (sessions.stream().anyMatch(s -> s.currentGame().orElseThrow().choosingMissingSuit()) && System.nanoTime() < timeout) Thread.sleep(20);
            assertTrue(sessions.stream().noneMatch(s -> s.currentGame().orElseThrow().choosingMissingSuit()));
            assertTrue(System.currentTimeMillis() >= deadline);
            var result = host.currentGame().orElseThrow();
            assertEquals(4, result.missingSuits().size());
            assertTrue(sessions.stream().allMatch(s -> s.currentGame().orElseThrow().missingSuits().equals(result.missingSuits())));
            var allowed = result.availableActions().stream().filter(a -> a.type() == PlayerActionType.DISCARD).findFirst().orElseThrow().relatedTiles();
            assertFalse(allowed.isEmpty());
            assertTrue(await(host.perform(PlayerActionType.DISCARD, List.of(allowed.getFirst()))).accepted());
        } finally { sessions.reversed().forEach(LanSession::close); }
    }

    private PlayerProfile player(String name) {
        return new PlayerProfile(new PlayerId(UUID.randomUUID().toString()), name, "", 0, 1);
    }

    private Seat localSeat(LanSession session) {
        return session.currentRoom().orElseThrow().players().stream()
                .filter(player -> player.profile().id().equals(session.localPlayer().id()))
                .map(RoomPlayer::seat).findFirst().orElseThrow();
    }

    private boolean allReady(RoomSnapshot room) {
        return room.players().size() == 4
                && room.players().stream().allMatch(player -> player.ready() && player.connected());
    }

    private <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(8, TimeUnit.SECONDS);
    }

    private void waitUntil(Check check) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!check.done() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(check.done(), "等待联机状态同步超时");
    }

    @FunctionalInterface
    private interface Check { boolean done(); }
}

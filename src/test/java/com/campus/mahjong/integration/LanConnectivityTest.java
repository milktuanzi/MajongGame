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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanConnectivityTest {
    @Test
    void settlesTwoRoundsAndRejectsUnauthorizedOrExtraRounds() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.NORTHERN, 2, Optional.of(128), 2, false, "");
        List<LanSession> clients = new ArrayList<>();
        LanSession host = await(LanSession.host(player("房主"), settings, 0, "127.0.0.1"));
        clients.add(host);
        try {
            for (String name : List.of("南", "西", "北")) {
                LanSession guest = await(LanSession.join(player(name), host.invitation()));
                clients.add(guest);
                await(guest.setReady(guest.currentRoom().orElseThrow().roomId(), guest.localPlayer().id(), true));
            }
            await(host.start(host.currentRoom().orElseThrow().roomId(), host.localPlayer().id()));
            waitUntil(() -> clients.stream().allMatch(client -> client.currentGame().isPresent()));
            for (int round = 1; round <= 2; round++) {
                playUntilDraw(clients);
                waitUntil(() -> clients.stream().allMatch(client -> client.currentSettlement().isPresent()));
                Settlement result = host.currentSettlement().orElseThrow();
                assertEquals(round, result.round());
                assertTrue(result.winner().isEmpty());
                assertEquals(0, result.changes().stream().mapToLong(ScoreChange::delta).sum());
                for (LanSession client : clients) assertEquals(result, client.currentSettlement().orElseThrow());
                assertThrows(Exception.class, () -> await(clients.get(1).nextRound()));
                if (round == 1) {
                    GameId next = await(host.nextRound());
                    waitUntil(() -> clients.stream().allMatch(client ->
                            client.currentGame().orElseThrow().gameId().equals(next)));
                    assertTrue(clients.stream().allMatch(client -> client.currentSettlement().isEmpty()));
                    assertThrows(Exception.class, () -> await(host.nextRound()));
                } else {
                    assertThrows(Exception.class, () -> await(host.nextRound()));
                }
            }
        } finally {
            clients.reversed().forEach(LanSession::close);
        }
    }

    private void playUntilDraw(List<LanSession> clients) throws Exception {
        for (int turn = 0; turn < 150; turn++) {
            GameSnapshot state = clients.get(0).currentGame().orElseThrow();
            if (state.status() == GameStatus.SETTLING) return;
            LanSession actor = clients.stream().filter(client -> localSeat(client) == state.currentTurn()).findFirst().orElseThrow();
            Tile tile = actor.currentGame().orElseThrow().drawnTile().orElseThrow();
            assertTrue(await(actor.perform(PlayerActionType.DISCARD, List.of(tile))).accepted());
            waitUntil(() -> clients.stream().allMatch(client ->
                    client.currentGame().orElseThrow().revision() > state.revision()));
            long claimRevision = clients.get(0).currentGame().orElseThrow().revision();
            for (LanSession client : clients) {
                if (client != actor) assertTrue(await(client.perform(PlayerActionType.PASS, List.of())).accepted());
            }
            waitUntil(() -> clients.stream().allMatch(client ->
                    client.currentGame().orElseThrow().revision() > claimRevision));
        }
        throw new AssertionError("牌墙耗尽后仍未结算");
    }

    @Test
    void fourClientsJoinReadyStartAndShareAnAuthoritativeRound() throws Exception {
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.NORTHERN,
                2, Optional.of(128), 4, false, "");
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
                    session.currentGame().orElseThrow().revision() == 1));

            List<LanSession> responders = sessions.stream()
                    .filter(session -> !session.owner())
                    .sorted(Comparator.comparing(session -> localSeat(session).ordinal())).toList();
            for (LanSession responder : responders) {
                assertTrue(await(responder.perform(PlayerActionType.PASS, List.of())).accepted());
            }
            waitUntil(() -> sessions.stream().allMatch(session ->
                    session.currentGame().orElseThrow().revision() == 2));
            assertEquals(Seat.SOUTH, host.currentGame().orElseThrow().currentTurn());
        } finally {
            sessions.reversed().forEach(LanSession::close);
        }
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

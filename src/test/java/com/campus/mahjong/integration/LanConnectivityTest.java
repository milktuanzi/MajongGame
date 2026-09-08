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

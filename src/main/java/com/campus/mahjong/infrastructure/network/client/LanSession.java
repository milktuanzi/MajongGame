package com.campus.mahjong.infrastructure.network.client;

import com.campus.mahjong.infrastructure.network.protocol.JsonMessageCodec;
import com.campus.mahjong.infrastructure.network.protocol.MessageEnvelope;
import com.campus.mahjong.infrastructure.network.protocol.MessageType;
import com.campus.mahjong.infrastructure.network.protocol.Payloads;
import com.campus.mahjong.infrastructure.network.server.GameServer;
import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.service.game.GameSessionService;
import com.campus.mahjong.model.service.room.FriendRoomService;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 当前客户端的热点房间会话，同时实现房间和牌局用例接口。 */
public final class LanSession implements FriendRoomService, GameSessionService, AutoCloseable {
    public static final int DEFAULT_PORT = 19090;
    private static final AtomicReference<LanSession> CURRENT = new AtomicReference<>();

    private final PlayerProfile localPlayer;
    private final boolean owner;
    private final String serverAddress;
    private final GameNetworkClient client;
    private final GameServer ownedServer;
    private final CopyOnWriteArrayList<Consumer<RoomSnapshot>> roomListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<GameSnapshot>> gameListeners = new CopyOnWriteArrayList<>();
    private final AutoCloseable networkSubscription;
    private volatile FriendRoomAccess access;
    private volatile RoomSnapshot room;
    private volatile GameSnapshot game;
    private final java.util.LinkedHashMap<String, com.campus.mahjong.model.ai.TeacherLesson> teacherHistory = new java.util.LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<com.campus.mahjong.model.ai.TeacherLesson>> teacherListeners = new CopyOnWriteArrayList<>();

    public synchronized List<com.campus.mahjong.model.ai.TeacherLesson> teacherHistory() { return List.copyOf(teacherHistory.values()); }
    public AutoCloseable observeTeacher(Consumer<com.campus.mahjong.model.ai.TeacherLesson> listener) {
        teacherListeners.add(listener); return () -> teacherListeners.remove(listener);
    }
    private void receiveLesson(com.campus.mahjong.model.ai.TeacherLesson lesson) {
        synchronized (this) {
            teacherHistory.put(lesson.id(), lesson);
            while (teacherHistory.size() > 100) teacherHistory.remove(teacherHistory.keySet().iterator().next());
        }
        teacherListeners.forEach(listener -> listener.accept(lesson));
    }
    public CompletionStage<RoomSnapshot> addBot() {
        return response(client.request(MessageType.ADD_BOT, requireRoom().roomId().value(), localPlayer.id().value(),
                requireRoom().revision(), "")).thenApply(result -> { updateRoom(result.room()); return result.room(); });
    }
    public CompletionStage<RoomSnapshot> removeBot(Seat seat) {
        return response(client.request(MessageType.REMOVE_BOT, requireRoom().roomId().value(), localPlayer.id().value(),
                requireRoom().revision(), new Payloads.BotSeat(seat))).thenApply(result -> { updateRoom(result.room()); return result.room(); });
    }

    private LanSession(PlayerProfile localPlayer, boolean owner, String serverAddress,
                       GameNetworkClient client, GameServer ownedServer) {
        this.localPlayer = localPlayer;
        this.owner = owner;
        this.serverAddress = serverAddress;
        this.client = client;
        this.ownedServer = ownedServer;
        this.networkSubscription = client.observe(this::onMessage);
    }

    public static CompletionStage<LanSession> host(PlayerProfile player, FriendRoomSettings settings) {
        return host(player, settings, DEFAULT_PORT, null);
    }

    /** 指定 0 端口可用于本机多客户端集成测试。 */
    public static CompletionStage<LanSession> host(PlayerProfile player, FriendRoomSettings settings,
                                                   int port, String advertisedHost) {
        return host(player, settings, port, advertisedHost, new com.campus.mahjong.model.ai.MockTeacherExplanationProvider());
    }

    /** 讲解端口注入；将来可替换为异步 HTTP 适配器，默认仍为本地 Mock。 */
    public static CompletionStage<LanSession> host(PlayerProfile player, FriendRoomSettings settings,
            int port, String advertisedHost, com.campus.mahjong.model.ai.TeacherExplanationProvider provider) {
        return virtualTask(() -> {
            GameServer server = GameServer.open(port, provider);
            GameNetworkClient client = null;
            try {
                client = GameNetworkClient.connect("127.0.0.1", server.port());
                String host = advertisedHost == null ? LanAddressResolver.advertisedAddress() : advertisedHost;
                LanSession session = new LanSession(player, true, host + ":" + server.port(), client, server);
                session.access = session.create(player.id(), settings).toCompletableFuture().join();
                return session;
            } catch (RuntimeException | IOException exception) {
                if (client != null) client.close();
                server.close();
                throw exception;
            }
        });
    }

    public static CompletionStage<LanSession> join(PlayerProfile player, String invitationText) {
        return virtualTask(() -> {
            LanInvitation invitation = LanInvitation.parse(invitationText);
            GameNetworkClient client = GameNetworkClient.connect(invitation.host(), invitation.port());
            try {
                LanSession session = new LanSession(player, false,
                        invitation.host() + ":" + invitation.port(), client, null);
                session.access = session.joinByInviteCode(player.id(), invitation.roomCode(), "")
                        .toCompletableFuture().join();
                return session;
            } catch (RuntimeException exception) {
                client.close();
                throw exception;
            }
        });
    }

    public static void install(LanSession session) {
        LanSession previous = CURRENT.getAndSet(Objects.requireNonNull(session));
        if (previous != null && previous != session) previous.close();
    }

    public static Optional<LanSession> current() { return Optional.ofNullable(CURRENT.get()); }

    public static void closeCurrent() {
        LanSession session = CURRENT.getAndSet(null);
        if (session != null) session.close();
    }

    public PlayerProfile localPlayer() { return localPlayer; }
    public boolean owner() { return owner; }
    public Optional<RoomSnapshot> currentRoom() { return Optional.ofNullable(room); }
    public Optional<GameSnapshot> currentGame() { return Optional.ofNullable(game); }
    public String roomCode() { return requireAccess().inviteCode(); }
    public String invitation() {
        FriendRoomAccess currentAccess = requireAccess();
        return serverAddress + "#" + currentAccess.inviteCode();
    }

    @Override
    public CompletionStage<FriendRoomAccess> create(PlayerId ownerId, FriendRoomSettings settings) {
        requireLocal(ownerId);
        return response(client.request(MessageType.CREATE_ROOM, "", ownerId.value(), -1,
                new Payloads.CreateRoom(localPlayer, settings))).thenApply(result -> {
            updateRoom(result.room());
            FriendRoomAccess created = new FriendRoomAccess(result.room(), result.inviteCode(),
                    serverAddress, result.reconnectToken());
            access = created;
            return created;
        });
    }

    @Override
    public CompletionStage<FriendRoomAccess> joinByInviteCode(PlayerId playerId, String inviteCode, String password) {
        requireLocal(playerId);
        return response(client.request(MessageType.JOIN_ROOM, "", playerId.value(), -1,
                new Payloads.JoinRoom(localPlayer, inviteCode, password))).thenApply(result -> {
            updateRoom(result.room());
            FriendRoomAccess joined = new FriendRoomAccess(result.room(), result.inviteCode(),
                    serverAddress, result.reconnectToken());
            access = joined;
            return joined;
        });
    }

    @Override
    public CompletionStage<RoomSnapshot> setReady(RoomId roomId, PlayerId playerId, boolean ready) {
        requireLocal(playerId);
        return response(client.request(MessageType.SET_READY, roomId.value(), playerId.value(),
                requireRoom().revision(), new Payloads.Ready(ready))).thenApply(result -> {
            updateRoom(result.room());
            return result.room();
        });
    }

    @Override
    public CompletionStage<RoomSnapshot> updateSettings(RoomId roomId, PlayerId ownerId,
                                                        FriendRoomSettings settings) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("初版暂不支持入房后修改规则"));
    }

    @Override
    public CompletionStage<Void> leave(RoomId roomId, PlayerId playerId) {
        requireLocal(playerId);
        return response(client.request(MessageType.LEAVE_ROOM, roomId.value(), playerId.value(),
                requireRoom().revision(), "")).thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<GameId> start(RoomId roomId, PlayerId ownerId) {
        requireLocal(ownerId);
        return response(client.request(MessageType.START_GAME, roomId.value(), ownerId.value(),
                requireRoom().revision(), "")).thenApply(result -> new GameId(result.gameId()));
    }

    @Override
    public CompletionStage<FriendRoomAccess> reconnect(PlayerId playerId, String reconnectToken) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("断线令牌重连将在下一阶段实现"));
    }

    @Override
    public AutoCloseable observe(RoomId roomId, Consumer<RoomSnapshot> listener) {
        RoomSnapshot currentRoom = requireRoom();
        if (!currentRoom.roomId().equals(roomId)) throw new IllegalArgumentException("不是当前房间");
        roomListeners.add(listener);
        listener.accept(currentRoom);
        return () -> roomListeners.remove(listener);
    }

    public AutoCloseable observeGame(Consumer<GameSnapshot> listener) {
        gameListeners.add(listener);
        if (game != null) listener.accept(game);
        return () -> gameListeners.remove(listener);
    }

    @Override
    public CompletionStage<GameSnapshot> snapshot(GameId gameId, PlayerId viewer) {
        requireLocal(viewer);
        GameSnapshot currentGame = requireGame();
        return currentGame.gameId().equals(gameId)
                ? CompletableFuture.completedFuture(currentGame)
                : CompletableFuture.failedFuture(new IllegalArgumentException("不是当前牌局"));
    }

    @Override
    public CompletionStage<List<ActionOption>> availableActions(GameId gameId, PlayerId playerId) {
        return snapshot(gameId, playerId).thenApply(GameSnapshot::availableActions);
    }

    @Override
    public CompletionStage<ActionResult> perform(ActionRequest request) {
        requireLocal(request.playerId());
        GameSnapshot currentGame = requireGame();
        if (!currentGame.gameId().equals(request.gameId()))
            return CompletableFuture.failedFuture(new IllegalArgumentException("不是当前牌局"));
        Payloads.PlayerAction payload = new Payloads.PlayerAction(request.type(), request.tiles());
        return client.request(MessageType.PLAYER_ACTION, requireRoom().roomId().value(),
                        localPlayer.id().value(), request.expectedRevision(), payload)
                .thenApply(envelope -> JsonMessageCodec.value(envelope.payload(), Payloads.Response.class))
                .thenApply(result -> new ActionResult(result.accepted(), result.message(), game));
    }

    public CompletionStage<ActionResult> perform(PlayerActionType type, List<Tile> tiles) {
        GameSnapshot snapshot = requireGame();
        return perform(new ActionRequest(snapshot.gameId(), localPlayer.id(), type, tiles, snapshot.revision()));
    }

    @Override
    public CompletionStage<Settlement> latestSettlement(GameId gameId) {
        GameSnapshot current = requireGame();
        if (!current.gameId().equals(gameId) || current.status() != GameStatus.FINISHED)
            return CompletableFuture.failedFuture(new IllegalStateException("全部轮次尚未结束"));
        var changes = current.players().stream().map(player -> new ScoreChange(player.playerId(),
                0, player.score(), player.score(), java.util.Map.<String, Integer>of())).toList();
        return CompletableFuture.completedFuture(new Settlement(gameId, current.currentRound(), changes,
                Optional.empty(), "全部轮次结束"));
    }

    @Override
    public CompletionStage<Void> reconnect(GameId gameId, PlayerId playerId) {
        requireLocal(playerId);
        return CompletableFuture.failedFuture(new UnsupportedOperationException("断线重连将在下一阶段实现"));
    }

    private void onMessage(MessageEnvelope envelope) {
        switch (envelope.messageType()) {
            case TEACHER_LESSON -> receiveLesson(JsonMessageCodec.value(envelope.payload(), com.campus.mahjong.model.ai.TeacherLesson.class));
            case ROOM_SNAPSHOT -> updateRoom(JsonMessageCodec.value(envelope.payload(), RoomSnapshot.class));
            case GAME_SNAPSHOT -> {
                game = JsonMessageCodec.value(envelope.payload(), GameSnapshot.class);
                gameListeners.forEach(listener -> listener.accept(game));
            }
            default -> { }
        }
    }

    private void updateRoom(RoomSnapshot snapshot) {
        if (snapshot == null) return;
        room = snapshot;
        roomListeners.forEach(listener -> listener.accept(snapshot));
    }

    private CompletionStage<Payloads.Response> response(CompletionStage<MessageEnvelope> stage) {
        return stage.thenApply(envelope -> JsonMessageCodec.value(envelope.payload(), Payloads.Response.class))
                .thenApply(result -> {
                    if (!result.accepted()) throw new IllegalStateException(result.message());
                    return result;
                });
    }

    private void requireLocal(PlayerId playerId) {
        if (!localPlayer.id().equals(playerId)) throw new SecurityException("不是当前玩家");
    }

    private FriendRoomAccess requireAccess() {
        if (access == null) throw new IllegalStateException("尚未加入房间");
        return access;
    }

    private RoomSnapshot requireRoom() {
        if (room == null) throw new IllegalStateException("尚未加入房间");
        return room;
    }

    private GameSnapshot requireGame() {
        if (game == null) throw new IllegalStateException("牌局尚未开始");
        return game;
    }

    private static <T> CompletionStage<T> virtualTask(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread.ofVirtual().name("mahjong-lan-connect").start(() -> {
            try { result.complete(task.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    @Override
    public void close() {
        try { networkSubscription.close(); } catch (Exception ignored) {}
        client.close();
        if (ownedServer != null) ownedServer.close();
        CURRENT.compareAndSet(this, null);
    }
}

package com.campus.mahjong.infrastructure.network.server;

import com.campus.mahjong.infrastructure.game.InMemoryGameSessionService;
import com.campus.mahjong.infrastructure.network.protocol.JsonMessageCodec;
import com.campus.mahjong.infrastructure.network.protocol.MessageEnvelope;
import com.campus.mahjong.infrastructure.network.protocol.MessageType;
import com.campus.mahjong.infrastructure.network.protocol.Payloads;
import com.campus.mahjong.model.common.MahjongTypes.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** 管理房间生命周期；每个 Room 的修改方法均串行执行。 */
final class RoomManager {
    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, Room> roomsById = new ConcurrentHashMap<>();
    private final InMemoryGameSessionService games;
    private final SecureRandom secureRandom = new SecureRandom();

    RoomManager(InMemoryGameSessionService games) { this.games = games; }

    void handle(ClientConnection connection, MessageEnvelope envelope) {
        if (envelope.protocolVersion() != MessageEnvelope.CURRENT_VERSION) {
            reply(connection, envelope, rejected("协议版本不兼容", null));
            return;
        }
        try {
            switch (envelope.messageType()) {
                case CREATE_ROOM -> create(connection, envelope);
                case JOIN_ROOM -> join(connection, envelope);
                case SET_READY -> roomFor(connection, envelope).setReady(connection, envelope);
                case START_GAME -> roomFor(connection, envelope).start(connection, envelope);
                case PLAYER_ACTION -> roomFor(connection, envelope).perform(connection, envelope);
                case LEAVE_ROOM -> roomFor(connection, envelope).leave(connection, envelope);
                case PING -> send(connection, MessageType.PONG, "", "", JsonMessageCodec.tree("pong"));
                default -> reply(connection, envelope, rejected("服务端不接受该消息类型", null));
            }
        } catch (RuntimeException exception) {
            Room room = connection.roomId().isBlank() ? null : roomsById.get(connection.roomId());
            reply(connection, envelope, rejected(messageOf(exception), room == null ? null : room.snapshot()));
        }
    }

    void tick() {
        for (Room room : roomsById.values()) {
            synchronized (room) {
                if (room.status == RoomStatus.PLAYING && room.gameId != null && games.expireMissingSuit(room.gameId)) room.broadcastGame();
            }
        }
    }

    void disconnected(ClientConnection connection) {
        if (connection.roomId().isBlank()) return;
        Room room = roomsById.get(connection.roomId());
        if (room != null) room.disconnected(connection);
    }

    private synchronized void create(ClientConnection connection, MessageEnvelope envelope) {
        if (connection.playerId() != null) throw new IllegalStateException("该连接已经加入房间");
        Payloads.CreateRoom request = JsonMessageCodec.value(envelope.payload(), Payloads.CreateRoom.class);
        String code = nextCode();
        Room room = new Room(new RoomId(UUID.randomUUID().toString()), code, request.settings(), request.player());
        roomsByCode.put(code, room);
        roomsById.put(room.id.value(), room);
        Member owner = room.members.get(Seat.EAST);
        owner.connection = connection;
        connection.bind(owner.profile.id(), room.id.value());
        RoomSnapshot snapshot = room.snapshot();
        reply(connection, envelope, accepted("房间创建成功", owner.reconnectToken, code, "", snapshot));
        room.broadcastRoom();
    }

    private synchronized void join(ClientConnection connection, MessageEnvelope envelope) {
        if (connection.playerId() != null) throw new IllegalStateException("该连接已经加入房间");
        Payloads.JoinRoom request = JsonMessageCodec.value(envelope.payload(), Payloads.JoinRoom.class);
        Room room = roomsByCode.get(request.inviteCode());
        if (room == null) throw new IllegalArgumentException("房间不存在，请检查邀请信息");
        Member member = room.join(request.player(), request.password(), connection);
        connection.bind(member.profile.id(), room.id.value());
        RoomSnapshot snapshot = room.snapshot();
        reply(connection, envelope, accepted("加入房间成功", member.reconnectToken,
                room.inviteCode, room.gameId == null ? "" : room.gameId.value(), snapshot));
        room.broadcastRoom();
        if (room.gameId != null) room.sendGameSnapshot(member);
    }

    private Room roomFor(ClientConnection connection, MessageEnvelope envelope) {
        if (connection.playerId() == null) throw new IllegalStateException("请先加入房间");
        if (!connection.playerId().value().equals(envelope.playerId())) throw new SecurityException("玩家身份不匹配");
        Room room = roomsById.get(connection.roomId());
        if (room == null || !room.id.value().equals(envelope.roomId())) throw new IllegalStateException("房间已关闭");
        return room;
    }

    private synchronized String nextCode() {
        String code;
        do { code = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1_000_000)); }
        while (roomsByCode.containsKey(code));
        return code;
    }

    private String token() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private final class Room {
        private final RoomId id;
        private final String inviteCode;
        private final FriendRoomSettings settings;
        private final EnumMap<Seat, Member> members = new EnumMap<>(Seat.class);
        private final PlayerId ownerId;
        private RoomStatus status = RoomStatus.WAITING;
        private GameId gameId;
        private long revision;

        Room(RoomId id, String inviteCode, FriendRoomSettings settings, PlayerProfile owner) {
            this.id = id;
            this.inviteCode = inviteCode;
            this.settings = settings;
            this.ownerId = owner.id();
            members.put(Seat.EAST, new Member(owner, Seat.EAST, true, true, token()));
        }

        synchronized Member join(PlayerProfile player, String password, ClientConnection connection) {
            if (status != RoomStatus.WAITING) throw new IllegalStateException("牌局已经开始");
            if (!settings.password().isBlank() && !settings.password().equals(password))
                throw new IllegalArgumentException("房间密码错误");
            Member existing = member(player.id());
            if (existing != null) {
                if (existing.connected()) throw new IllegalStateException("该玩家已经在房间中");
                existing.connection = connection;
                existing.ready = false;
                revision++;
                return existing;
            }
            if (members.size() >= 4) throw new IllegalStateException("房间已满");
            if (members.values().stream().anyMatch(item -> item.profile.nickname().equals(player.nickname())))
                throw new IllegalStateException("房间内昵称不能重复");
            Seat seat = firstFreeSeat();
            Member joined = new Member(player, seat, false, false, token());
            joined.connection = connection;
            members.put(seat, joined);
            revision++;
            return joined;
        }

        synchronized void setReady(ClientConnection connection, MessageEnvelope envelope) {
            requireWaiting();
            Member member = requireMember(connection);
            Payloads.Ready request = JsonMessageCodec.value(envelope.payload(), Payloads.Ready.class);
            member.ready = member.owner || request.ready();
            revision++;
            RoomSnapshot snapshot = snapshot();
            reply(connection, envelope, accepted(member.ready ? "已准备" : "已取消准备",
                    member.reconnectToken, inviteCode, "", snapshot));
            broadcastRoom();
        }

        synchronized void start(ClientConnection connection, MessageEnvelope envelope) {
            requireWaiting();
            Member member = requireMember(connection);
            if (!member.profile.id().equals(ownerId)) throw new SecurityException("只有房主可以开始游戏");
            if (members.size() != 4 || members.values().stream().anyMatch(item -> !item.ready || !item.connected()))
                throw new IllegalStateException("必须四位玩家全部在线并准备");
            gameId = games.create(snapshot(), secureRandom.nextLong(), 1);
            status = RoomStatus.PLAYING;
            revision++;
            reply(connection, envelope, accepted("游戏开始", member.reconnectToken,
                    inviteCode, gameId.value(), snapshot()));
            broadcastRoom();
            broadcastGame();
        }

        synchronized void perform(ClientConnection connection, MessageEnvelope envelope) {
            if (status != RoomStatus.PLAYING || gameId == null) throw new IllegalStateException("牌局尚未开始");
            if (games.expireMissingSuit(gameId)) broadcastGame();
            Member member = requireMember(connection);
            Payloads.PlayerAction payload = JsonMessageCodec.value(envelope.payload(), Payloads.PlayerAction.class);
            ActionRequest request = new ActionRequest(gameId, member.profile.id(), payload.type(),
                    payload.tiles(), envelope.expectedRevision());
            ActionResult result = games.perform(request).toCompletableFuture().join();
            if (!result.accepted()) {
                reply(connection, envelope, rejected(result.message(), snapshot()));
                if (result.snapshot() != null) sendGame(connection, result.snapshot());
                return;
            }
            reply(connection, envelope, accepted(result.message(), member.reconnectToken,
                    inviteCode, gameId.value(), snapshot()));
            broadcastGame();
        }

        synchronized void leave(ClientConnection connection, MessageEnvelope envelope) {
            Member member = requireMember(connection);
            if (member.owner) {
                status = RoomStatus.CLOSED;
                revision++;
                reply(connection, envelope, accepted("房间已关闭", member.reconnectToken,
                        inviteCode, gameId == null ? "" : gameId.value(), snapshot()));
                broadcastRoom();
                roomsByCode.remove(inviteCode);
                roomsById.remove(id.value());
            } else if (status == RoomStatus.WAITING) {
                members.remove(member.seat);
                revision++;
                reply(connection, envelope, accepted("已离开房间", member.reconnectToken,
                        inviteCode, "", snapshot()));
                broadcastRoom();
            } else {
                member.connection = null;
                revision++;
                reply(connection, envelope, accepted("已离开牌局", member.reconnectToken,
                        inviteCode, gameId.value(), snapshot()));
                broadcastRoom();
            }
        }

        synchronized void disconnected(ClientConnection connection) {
            Member member = member(connection.playerId());
            if (member == null || member.connection != connection) return;
            member.connection = null;
            if (status == RoomStatus.WAITING && !member.owner) members.remove(member.seat);
            revision++;
            broadcastRoom();
        }

        synchronized RoomSnapshot snapshot() {
            List<RoomPlayer> players = members.values().stream()
                    .map(member -> new RoomPlayer(member.profile, member.seat, member.owner,
                            member.ready, member.connected())).toList();
            return new RoomSnapshot(id, RoomKind.FRIEND, settings.mode(), status, players,
                    4, Optional.of(settings), revision);
        }

        synchronized void broadcastRoom() {
            RoomSnapshot snapshot = snapshot();
            MessageEnvelope envelope = push(MessageType.ROOM_SNAPSHOT, id.value(), "",
                    snapshot.revision(), snapshot);
            connectedMembers().forEach(member -> member.connection.send(envelope));
        }

        synchronized void broadcastGame() {
            connectedMembers().forEach(this::sendGameSnapshot);
        }

        synchronized void sendGameSnapshot(Member member) {
            GameSnapshot snapshot = games.snapshot(gameId, member.profile.id()).toCompletableFuture().join();
            sendGame(member.connection, snapshot);
        }

        private void sendGame(ClientConnection connection, GameSnapshot snapshot) {
            connection.send(push(MessageType.GAME_SNAPSHOT, id.value(), connection.playerId().value(),
                    snapshot.revision(), snapshot));
        }

        private List<Member> connectedMembers() {
            return members.values().stream().filter(Member::connected).toList();
        }

        private Member requireMember(ClientConnection connection) {
            Member member = member(connection.playerId());
            if (member == null || member.connection != connection) throw new SecurityException("玩家不在该房间");
            return member;
        }

        private Member member(PlayerId playerId) {
            if (playerId == null) return null;
            return members.values().stream().filter(item -> item.profile.id().equals(playerId)).findFirst().orElse(null);
        }

        private Seat firstFreeSeat() {
            for (Seat seat : Seat.values()) if (!members.containsKey(seat)) return seat;
            throw new IllegalStateException("房间已满");
        }

        private void requireWaiting() {
            if (status != RoomStatus.WAITING) throw new IllegalStateException("房间已经开始游戏");
        }
    }

    private static final class Member {
        private final PlayerProfile profile;
        private final Seat seat;
        private final boolean owner;
        private final String reconnectToken;
        private boolean ready;
        private ClientConnection connection;

        Member(PlayerProfile profile, Seat seat, boolean owner, boolean ready, String reconnectToken) {
            this.profile = profile;
            this.seat = seat;
            this.owner = owner;
            this.ready = ready;
            this.reconnectToken = reconnectToken;
        }

        boolean connected() { return connection != null; }
    }

    private static void reply(ClientConnection connection, MessageEnvelope request, Payloads.Response response) {
        connection.send(new MessageEnvelope(MessageEnvelope.CURRENT_VERSION, request.requestId(),
                MessageType.RESPONSE, request.roomId(), request.playerId(), request.expectedRevision(),
                JsonMessageCodec.tree(response)));
    }

    private static void send(ClientConnection connection, MessageType type, String roomId,
                             String playerId, com.fasterxml.jackson.databind.JsonNode payload) {
        connection.send(new MessageEnvelope(MessageEnvelope.CURRENT_VERSION, "", type,
                roomId, playerId, -1, payload));
    }

    private static MessageEnvelope push(MessageType type, String roomId, String playerId,
                                        long revision, Object payload) {
        return new MessageEnvelope(MessageEnvelope.CURRENT_VERSION, "", type, roomId,
                playerId, revision, JsonMessageCodec.tree(payload));
    }

    private static Payloads.Response accepted(String message, String token, String inviteCode,
                                               String gameId, RoomSnapshot snapshot) {
        return new Payloads.Response(true, message, token, inviteCode, gameId, snapshot);
    }

    private static Payloads.Response rejected(String message, RoomSnapshot snapshot) {
        return new Payloads.Response(false, message, "", "", "", snapshot);
    }

    private static String messageOf(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "操作失败" : current.getMessage();
    }
}

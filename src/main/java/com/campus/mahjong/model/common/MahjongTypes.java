package com.campus.mahjong.model.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 跨模块共享的只读值对象与枚举。 */
public final class MahjongTypes {
    private MahjongTypes() {}

    public enum ModeCode { SICHUAN, RED_CENTER }
    public enum RoomKind { FRIEND }
    public enum RoomStatus { WAITING, READY_CHECK, PLAYING, FINISHED, CLOSED }
    public enum GameStatus { PREPARING, DEALING, PLAYING, SETTLING, FINISHED, ABORTED }
    public enum Seat { EAST, SOUTH, WEST, NORTH }
    public enum PlayerActionType { DRAW, DISCARD, PENG, GANG, HU, PASS, READY, DING_QUE, EXCHANGE_THREE }
    public enum PageId { HOME, FRIEND_ROOM_SETUP, ROOM, GAME, RESULT }

    public record PlayerId(String value) {
        public PlayerId { requireText(value, "playerId"); }
    }
    public record RoomId(String value) {
        public RoomId { requireText(value, "roomId"); }
    }
    public record GameId(String value) {
        public GameId { requireText(value, "gameId"); }
    }

    public record PlayerProfile(PlayerId id, String nickname, String avatarUrl,
                                long coins, int level) {
        public PlayerProfile {
            Objects.requireNonNull(id);
            requireText(nickname, "nickname");
            if (coins < 0 || level < 1) throw new IllegalArgumentException("invalid profile values");
        }
    }

    public record ModeInfo(ModeCode code, String displayName, String description,
                           int playerCount, boolean enabled) {
        public ModeInfo {
            Objects.requireNonNull(code);
            requireText(displayName, "displayName");
            if (playerCount < 2 || playerCount > 4) throw new IllegalArgumentException("playerCount");
        }
    }

    public record FriendRoomSettings(ModeCode mode, int baseMultiplier,
                                     Optional<Integer> scoreCap, int rounds,
                                     boolean allowSpectators, String password, boolean teachingMode) {
        public FriendRoomSettings(ModeCode mode, int baseMultiplier, Optional<Integer> scoreCap,
                                  int rounds, boolean allowSpectators, String password) {
            this(mode, baseMultiplier, scoreCap, rounds, allowSpectators, password, false);
        }
        public FriendRoomSettings {
            Objects.requireNonNull(mode); Objects.requireNonNull(scoreCap);
            password = password == null ? "" : password;
            if (baseMultiplier <= 0 || rounds <= 0 || scoreCap.orElse(1) <= 0) throw new IllegalArgumentException("invalid room settings");
        }
    }

    /** 创建好友房后返回给房主，用于跨电脑邀请。 */
    public record FriendRoomAccess(RoomSnapshot room, String inviteCode,
                                   String serverAddress, String reconnectToken) {
        public FriendRoomAccess {
            Objects.requireNonNull(room); requireText(inviteCode, "inviteCode");
            requireText(serverAddress, "serverAddress"); requireText(reconnectToken, "reconnectToken");
        }
    }

    /** 好友之间跨多局累计的积分排名。 */
    public record FriendScoreEntry(PlayerId playerId, String nickname,
                                   long totalScore, int games, int wins) {
        public FriendScoreEntry {
            Objects.requireNonNull(playerId); requireText(nickname, "nickname");
            if (games < 0 || wins < 0 || wins > games) throw new IllegalArgumentException("invalid score entry");
        }
    }

    public record RoomSnapshot(RoomId roomId, RoomKind kind, ModeCode mode,
                               RoomStatus status, List<RoomPlayer> players,
                               int requiredPlayers, Optional<FriendRoomSettings> settings,
                               long revision) {
        public RoomSnapshot {
            Objects.requireNonNull(roomId); Objects.requireNonNull(kind); Objects.requireNonNull(mode);
            Objects.requireNonNull(status); players = List.copyOf(players); Objects.requireNonNull(settings);
            if (revision < 0) throw new IllegalArgumentException("revision");
        }
    }

    public record RoomPlayer(PlayerProfile profile, Seat seat, boolean owner, boolean ready,
                             boolean connected, boolean bot) {
        public RoomPlayer(PlayerProfile profile, Seat seat, boolean owner, boolean ready, boolean connected) {
            this(profile, seat, owner, ready, connected, false);
        }
    }
    public record Tile(String code) { public Tile { requireText(code, "tile code"); } }

    public record GameSnapshot(GameId gameId, RoomId roomId, GameStatus status,
                               int currentRound, Seat dealer, Seat currentTurn,
                               List<PlayerPublicState> players, List<Tile> ownHand,
                               Optional<Tile> drawnTile, List<ActionOption> availableActions,
                               int wallRemaining, long revision, boolean waitingForClaims, long actionStartedAtMillis,
                               java.util.Set<Seat> winners, List<com.campus.mahjong.model.game.ScoreEntry> ledger,
                               boolean choosingMissingSuit, long missingSuitDeadline,
                               Map<Seat, com.campus.mahjong.model.game.TileType.Suit> missingSuits,
                               java.util.Set<Seat> missingSuitReady,
                               boolean exchangingTiles, long exchangeDeadline, java.util.Set<Seat> exchangeReady,
                               List<Tile> ownExchangeSelection, List<Tile> receivedExchangeTiles,
                               Optional<com.campus.mahjong.model.game.ExchangeDirection> exchangeDirection,
                               List<com.campus.mahjong.model.game.WinEvent> winEvents) {
        public GameSnapshot {
            players = List.copyOf(players); ownHand = List.copyOf(ownHand);
            winners = java.util.Set.copyOf(winners); ledger = List.copyOf(ledger);
            missingSuits = Map.copyOf(missingSuits); missingSuitReady = java.util.Set.copyOf(missingSuitReady);
            exchangeReady = java.util.Set.copyOf(exchangeReady);
            ownExchangeSelection = List.copyOf(ownExchangeSelection); receivedExchangeTiles = List.copyOf(receivedExchangeTiles);
            Objects.requireNonNull(exchangeDirection);
            winEvents = List.copyOf(winEvents);
            Objects.requireNonNull(drawnTile);
            availableActions = List.copyOf(availableActions);
            if (wallRemaining < 0 || revision < 0) throw new IllegalArgumentException("invalid game snapshot");
        }
    }

    public record PlayerPublicState(PlayerId playerId, Seat seat, int handTileCount,
                                    List<Tile> discards, List<List<Tile>> exposedGroups,
                                    long score, boolean connected) {
        public PlayerPublicState {
            discards = List.copyOf(discards);
            exposedGroups = exposedGroups.stream().map(List::copyOf).toList();
        }
    }

    public record ActionRequest(GameId gameId, PlayerId playerId, PlayerActionType type,
                                List<Tile> tiles, long expectedRevision) {
        public ActionRequest { tiles = List.copyOf(tiles); }
    }

    public record ActionOption(PlayerActionType type, List<Tile> relatedTiles,
                               boolean mandatory) {
        public ActionOption { relatedTiles = List.copyOf(relatedTiles); }
    }

    public record ActionResult(boolean accepted, String message, GameSnapshot snapshot) {}

    public record Settlement(GameId gameId, int round, List<ScoreChange> changes,
                             Optional<Seat> winner, String reason) {
        public Settlement { changes = List.copyOf(changes); Objects.requireNonNull(winner); }
    }
    public record ScoreChange(PlayerId playerId, long before, long delta, long after,
                              Map<String, Integer> scoringItems) {
        public ScoreChange { scoringItems = Map.copyOf(scoringItems); }
    }

    public record ApiError(String code, String message, Map<String, String> details,
                           Instant occurredAt) {
        public ApiError { details = Map.copyOf(details); }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
    }
}

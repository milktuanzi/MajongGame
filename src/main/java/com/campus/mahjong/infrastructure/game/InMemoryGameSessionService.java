package com.campus.mahjong.infrastructure.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.game.MahjongRound;
import com.campus.mahjong.model.game.RoundOutcome;
import com.campus.mahjong.model.game.RoundPhase;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.service.game.GameSessionService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 可被单机和房主服务复用的 GameSessionService 实现。
 * 网络层只需把请求按顺序交给该服务，不允许客户端直接修改牌局快照。
 */
public final class InMemoryGameSessionService implements GameSessionService {
    private final Map<GameId, Context> sessions = new LinkedHashMap<>();

    public synchronized GameId create(RoomSnapshot room, long seed, int roundNumber) {
        FriendRoomSettings settings = room.settings().orElseThrow(() -> new IllegalArgumentException("好友房缺少规则设置"));
        if (room.players().size() != 4) throw new IllegalArgumentException("必须有四位玩家才能开局");
        EnumMap<Seat, PlayerId> players = new EnumMap<>(Seat.class);
        room.players().forEach(player -> players.put(player.seat(), player.profile().id()));
        GameId gameId = new GameId(UUID.randomUUID().toString());
        sessions.put(gameId, new Context(gameId, room.roomId(), roundNumber,
                MahjongRound.start(settings, seed), players));
        return gameId;
    }

    @Override
    public CompletionStage<GameSnapshot> snapshot(GameId gameId, PlayerId viewer) {
        try { return CompletableFuture.completedFuture(snapshot(require(gameId), viewer)); }
        catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }

    @Override
    public CompletionStage<List<ActionOption>> availableActions(GameId gameId, PlayerId playerId) {
        try {
            Context context = require(gameId);
            synchronized (context) {
                Seat seat = context.seatOf(playerId);
                return CompletableFuture.completedFuture(actionOptions(context.round, seat));
            }
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<ActionResult> perform(ActionRequest request) {
        try {
            Context context = require(request.gameId());
            synchronized (context) {
                Seat seat = context.seatOf(request.playerId());
                apply(context.round, seat, request);
                return CompletableFuture.completedFuture(new ActionResult(true, "操作成功",
                        snapshot(context, request.playerId())));
            }
        } catch (RuntimeException exception) {
            Context context = sessions.get(request.gameId());
            GameSnapshot latest = context == null ? null : snapshot(context, request.playerId());
            return CompletableFuture.completedFuture(new ActionResult(false, exception.getMessage(), latest));
        }
    }

    @Override
    public CompletionStage<Settlement> latestSettlement(GameId gameId) {
        try {
            Context context = require(gameId);
            RoundOutcome outcome = context.round.outcome().orElseThrow(() -> new IllegalStateException("本局尚未结束"));
            List<ScoreChange> changes = new ArrayList<>();
            for (Seat seat : Seat.values()) {
                long delta = outcome.scoreChanges().getOrDefault(seat, 0L);
                changes.add(new ScoreChange(context.players.get(seat), 0, delta, delta, Map.of()));
            }
            return CompletableFuture.completedFuture(new Settlement(gameId, context.roundNumber, changes,
                    outcome.winner(), outcome.reason()));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override public CompletionStage<Void> reconnect(GameId gameId, PlayerId playerId) {
        try { require(gameId).seatOf(playerId); return CompletableFuture.completedFuture(null); }
        catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }

    private void apply(MahjongRound round, Seat seat, ActionRequest request) {
        switch (request.type()) {
            case DISCARD -> round.discard(seat, requiredTile(request), request.expectedRevision());
            case HU -> {
                if (round.phase() == RoundPhase.WAITING_FOR_DISCARD) round.declareSelfDraw(seat, request.expectedRevision());
                else round.submitClaim(seat, PlayerActionType.HU, request.expectedRevision());
            }
            case CHI, PENG -> round.submitClaim(seat, request.type(), request.expectedRevision());
            case PASS -> round.submitClaim(seat, PlayerActionType.PASS, request.expectedRevision());
            case GANG -> {
                if (round.phase() == RoundPhase.WAITING_FOR_DISCARD)
                    round.declareSelfGang(seat, requiredTile(request), request.expectedRevision());
                else round.submitClaim(seat, PlayerActionType.GANG, request.expectedRevision());
            }
            default -> throw new IllegalArgumentException("暂不支持操作: " + request.type());
        }
    }

    private TileType requiredTile(ActionRequest request) {
        if (request.tiles().isEmpty()) throw new IllegalArgumentException("该操作必须指定麻将牌");
        return TileType.fromDisplayName(request.tiles().get(0).code());
    }

    private GameSnapshot snapshot(Context context, PlayerId viewer) {
        Seat viewerSeat = context.seatOf(viewer);
        MahjongRound round = context.round;
        List<PlayerPublicState> players = new ArrayList<>();
        for (Seat seat : Seat.values()) {
            List<Tile> discardTiles = round.discards(seat).stream().map(tile -> new Tile(tile.displayName())).toList();
            List<List<Tile>> groups = round.melds(seat).stream()
                    .map(meld -> meld.tiles().stream().map(tile -> new Tile(tile.displayName())).toList()).toList();
            players.add(new PlayerPublicState(context.players.get(seat), seat, round.hand(seat).size(),
                    discardTiles, groups, 0, true));
        }
        GameStatus status = round.phase() == RoundPhase.FINISHED ? GameStatus.SETTLING : GameStatus.PLAYING;
        List<Tile> ownHand = round.organizedHand(viewerSeat).stream()
                .map(tile -> new Tile(tile.displayName())).toList();
        Optional<Tile> drawnTile = round.drawnTile(viewerSeat).map(tile -> new Tile(tile.displayName()));
        return new GameSnapshot(context.gameId, context.roomId, status, context.roundNumber,
                Seat.EAST, round.currentTurn(), players, ownHand, drawnTile,
                actionOptions(round, viewerSeat), round.wallRemaining(), round.revision());
    }

    private List<ActionOption> actionOptions(MahjongRound round, Seat seat) {
        return round.legalActions(seat).stream().map(type -> {
            List<Tile> related = type == PlayerActionType.GANG
                    ? round.selfGangTiles(seat).stream().map(tile -> new Tile(tile.displayName())).toList()
                    : List.of();
            return new ActionOption(type, related, false);
        }).toList();
    }

    private synchronized Context require(GameId gameId) {
        Context context = sessions.get(gameId);
        if (context == null) throw new IllegalArgumentException("牌局不存在: " + gameId.value());
        return context;
    }

    private record Context(GameId gameId, RoomId roomId, int roundNumber,
                           MahjongRound round, EnumMap<Seat, PlayerId> players) {
        Seat seatOf(PlayerId playerId) {
            return players.entrySet().stream().filter(entry -> entry.getValue().equals(playerId))
                    .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("玩家不在本局中"));
        }
    }
}

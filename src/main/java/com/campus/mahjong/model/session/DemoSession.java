package com.campus.mahjong.model.session;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.game.MahjongRound;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.game.RoundOutcome;
import com.campus.mahjong.model.game.RoundPhase;
import com.campus.mahjong.model.game.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 页面联调阶段使用的内存会话模型；接入网络后由服务端快照替换。 */
public final class DemoSession {
    public static boolean owner = true;
    public static String roomCode = "836204";
    public static String nickname = "清风客";
    public static ModeCode mode = ModeCode.SICHUAN;
    public static String rounds = "8 轮";
    public static String multiplier = "2 倍";
    public static String scoreCap = "128 分";

    private static final List<PlayerState> players = new ArrayList<>();
    private static final List<String> hand = new ArrayList<>();
    private static final List<String> discards = new ArrayList<>();
    private static final Map<String, Long> friendScores = new LinkedHashMap<>();
    private static final Map<String, Long> roomScores = new LinkedHashMap<>();
    private static int currentRound = 1;
    private static int remainingTiles = 56;
    private static int turnIndex;
    private static boolean roomStarted;
    /** 弃牌停顿结束后才开放的本机响应窗口。 */
    private static boolean claimPromptOpen;
    private static RoundResult lastResult;
    private static MahjongRound roundEngine;

    static {
        friendScores.put("山海客", 2680L);
        friendScores.put("清风客", 2240L);
        friendScores.put("小满", 1860L);
        friendScores.put("知夏", 1452L);
        resetPlayers();
        resetHand();
    }

    private DemoSession() {}

    public static void createRoom(String playerName, ModeCode selectedMode,
                                  String selectedRounds, String selectedMultiplier,
                                  String selectedScoreCap) {
        owner = true;
        nickname = playerName;
        mode = ModeCode.SICHUAN;
        rounds = selectedRounds;
        multiplier = selectedMultiplier;
        scoreCap = selectedScoreCap;
        roomCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        beginRoom();
    }

    public static void joinRoom(String playerName, String code) {
        owner = false;
        nickname = playerName;
        roomCode = code;
        beginRoom();
    }

    private static void beginRoom() {
        currentRound = 1;
        roomStarted = false;
        lastResult = null;
        claimPromptOpen = false;
        roomScores.clear();
        resetPlayers();
        resetHand();
    }

    private static void resetPlayers() {
        players.clear();
        players.add(new PlayerState(nickname, "东", owner, owner, true));
        players.add(new PlayerState("山海客", "南", false, true, true));
        players.add(new PlayerState("小满", "西", false, true, true));
        players.add(new PlayerState("知夏", "北", false, true, true));
        for (PlayerState player : players) roomScores.putIfAbsent(player.name(), 0L);
    }

    private static void resetHand() {
        hand.clear();
        Collections.addAll(hand, "一万", "二万", "三万", "五万", "五万", "六万",
                "一筒", "二筒", "三筒", "七条", "八条", "九条", "中", "中");
        discards.clear();
        remainingTiles = 56;
        turnIndex = 0;
    }

    public static List<PlayerState> players() { return List.copyOf(players); }
    public static Seat localSeat() { return Seat.EAST; }
    public static String playerName(Seat seat) { return players.get(seat.ordinal()).name(); }
    public static PlayerId playerId(String playerName) {
        return new PlayerId(UUID.nameUUIDFromBytes(("mahjong-player:" + playerName)
                .getBytes(StandardCharsets.UTF_8)).toString());
    }
    public static List<String> hand() {
        if (roundEngine == null) return List.copyOf(hand);
        return roundEngine.hand(Seat.EAST).stream().map(TileType::displayName).toList();
    }
    public static List<String> organizedHand() {
        if (roundEngine == null) return hand().stream().sorted().toList();
        return roundEngine.organizedHand(Seat.EAST).stream().map(TileType::displayName).toList();
    }
    public static Optional<String> drawnTile() {
        if (roundEngine == null) return Optional.empty();
        return roundEngine.drawnTile(Seat.EAST).map(TileType::displayName);
    }
    /** 已副露的碰、杠牌组，供本机与联机快照的牌桌视图复用。 */
    public static List<Meld> localMelds() {
        return roundEngine == null ? List.of() : roundEngine.melds(Seat.EAST);
    }
    public static List<String> discards() {
        if (roundEngine == null) return List.copyOf(discards);
        List<String> all = new ArrayList<>();
        for (Seat seat : Seat.values()) roundEngine.discards(seat).stream().map(TileType::displayName).forEach(all::add);
        return List.copyOf(all);
    }
    /** 各座位公开弃牌；桌面按座位分别渲染，不能混入中央公共列表。 */
    public static List<String> discardsForSeat(Seat seat) {
        if (roundEngine == null) return List.of();
        return roundEngine.discards(seat).stream().map(TileType::displayName).toList();
    }
    public static Map<String, Long> roomScores() { return Map.copyOf(roomScores); }
    public static int currentRound() { return currentRound; }
    public static int totalRounds() { return Integer.parseInt(rounds.replace("轮", "").trim()); }
    public static int remainingTiles() { return roundEngine == null ? remainingTiles : roundEngine.wallRemaining(); }
    public static String currentTurnName() { return players.get(turnIndex).name(); }
    public static boolean roomStarted() { return roomStarted; }
    public static RoundResult lastResult() { return lastResult; }

    public static void setLocalReady(boolean ready) {
        PlayerState local = players.get(0);
        players.set(0, new PlayerState(local.name(), local.seat(), local.owner(), ready, true));
    }

    public static boolean allReady() {
        return players.size() == 4 && players.stream().allMatch(PlayerState::ready);
    }

    public static void startGame() {
        roomStarted = true;
        mode = ModeCode.SICHUAN;
        FriendRoomSettings settings = new FriendRoomSettings(ModeCode.SICHUAN,
                Integer.parseInt(multiplier.replace("倍", "").trim()),
                scoreCap.equals("不封顶") ? Optional.empty() : Optional.of(Integer.parseInt(scoreCap.replace("分", "").trim())),
                totalRounds(), false, "");
        roundEngine = MahjongRound.start(settings, System.nanoTime());
        lastResult = null;
        claimPromptOpen = false;
    }

    /** 本地联调：本机使用真实状态机，其他三家暂由自动玩家选择弃牌并放弃响应。 */
    public static String discard(String tile) { return discard(tile, false); }

    public static String discard(String tile, boolean drawnTile) {
        if (roundEngine == null) return "牌局尚未开始";
        try {
            roundEngine.discard(Seat.EAST, TileType.fromDisplayName(tile), drawnTile, roundEngine.revision());
            claimPromptOpen = false;
            return "已打出 " + tile + "，2 秒后轮到下一家";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    /**
     * 每次只推进一个可见动作。控制器在每次弃牌后等待两秒再调用它，
     * 因而不会再把三家自动玩家的出牌压缩到同一帧。
     */
    public static String advanceSimulationStep() {
        if (roundEngine == null) return "牌局尚未开始";
        if (roundEngine.phase() == RoundPhase.FINISHED) return finishMessage();
        if (roundEngine.phase() == RoundPhase.WAITING_FOR_CLAIMS) {
            if (claimPromptOpen) return "等待你的碰、杠、胡或过操作";
            if (hasLocalClaimOption()) {
                passBotClaims();
                claimPromptOpen = true;
                return "对手打出 " + roundEngine.lastDiscard().orElseThrow().displayName() + "，请选择碰、杠、胡或过";
            }
            roundEngine.passRemainingClaims();
            if (roundEngine.phase() == RoundPhase.FINISHED) return finishMessage();
        }
        if (roundEngine.currentTurn() == Seat.EAST) return "轮到你摸牌并出牌";

        Seat bot = roundEngine.currentTurn();
        List<TileType> botHand = roundEngine.hand(bot);
        TileType choice = chooseTestDiscard(bot, botHand);
        boolean isDrawn = roundEngine.drawnTile(bot).filter(choice::equals).isPresent();
        roundEngine.discard(bot, choice, isDrawn, roundEngine.revision());
        claimPromptOpen = false;
        return players.get(bot.ordinal()).name() + " 打出 " + choice.displayName() + "，等待 2 秒";
    }

    public static boolean shouldAdvanceSimulation() {
        return roundEngine != null && roundEngine.phase() != RoundPhase.FINISHED
                && !claimPromptOpen
                && !(roundEngine.phase() == RoundPhase.WAITING_FOR_DISCARD && roundEngine.currentTurn() == Seat.EAST);
    }

    private static String finishMessage() {
        applyOutcome(roundEngine.outcome().orElseThrow());
        return "牌局结束：" + lastResult.reason();
    }

    /** 自动玩家优先弃出可供本机碰/杠的牌，使本地联调无需等待很久。 */
    private static TileType chooseTestDiscard(Seat bot, List<TileType> botHand) {
        List<TileType> local = roundEngine.hand(Seat.EAST);
        for (int needed : List.of(3, 2)) {
            Optional<TileType> candidate = botHand.stream()
                    .filter(tile -> local.stream().filter(tile::equals).count() >= needed)
                    .findFirst();
            if (candidate.isPresent()) return candidate.get();
        }
        return roundEngine.drawnTile(bot).orElse(botHand.get(botHand.size() - 1));
    }

    private static void passBotClaims() {
        Seat discarder = roundEngine.lastDiscarder().orElseThrow();
        long revision = roundEngine.revision();
        for (Seat seat : Seat.values()) {
            if (seat != Seat.EAST && seat != discarder) {
                roundEngine.submitClaim(seat, PlayerActionType.PASS, revision);
            }
        }
    }

    public static EnumSet<PlayerActionType> localActions() {
        return roundEngine == null ? EnumSet.noneOf(PlayerActionType.class) : roundEngine.legalActions(Seat.EAST);
    }

    public static boolean isAwaitingLocalClaim() {
        return claimPromptOpen && hasLocalClaimOption();
    }

    private static boolean hasLocalClaimOption() {
        if (roundEngine == null || roundEngine.phase() != RoundPhase.WAITING_FOR_CLAIMS
                || roundEngine.lastDiscarder().orElse(Seat.EAST) == Seat.EAST) return false;
        EnumSet<PlayerActionType> actions = roundEngine.legalActions(Seat.EAST);
        return actions.contains(PlayerActionType.PENG) || actions.contains(PlayerActionType.GANG)
                || actions.contains(PlayerActionType.HU);
    }

    public static String submitLocalClaim(PlayerActionType action) {
        if (!isAwaitingLocalClaim()) return "当前没有可响应的对手弃牌";
        try {
            roundEngine.submitClaim(Seat.EAST, action, roundEngine.revision());
            claimPromptOpen = false;
            if (roundEngine.phase() == RoundPhase.FINISHED) {
                applyOutcome(roundEngine.outcome().orElseThrow());
                return "胡牌成功";
            }
            return switch (action) {
                case PENG -> "碰牌成功，请选择一张手牌打出";
                case GANG -> "杠牌成功，已补摸一张牌";
                case PASS -> "已选择过，牌局继续";
                default -> "操作完成";
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    public static boolean canLocalWin() {
        EnumSet<PlayerActionType> actions = localActions();
        return actions.contains(PlayerActionType.HU)
                && (actions.contains(PlayerActionType.DISCARD) || isAwaitingLocalClaim());
    }

    public static List<String> localGangTiles() {
        if (roundEngine == null) return List.of();
        return roundEngine.selfGangTiles(Seat.EAST).stream().map(TileType::displayName).toList();
    }

    public static String declareLocalGang(String tile) {
        if (roundEngine == null) return "牌局尚未开始";
        try {
            roundEngine.declareSelfGang(Seat.EAST, TileType.fromDisplayName(tile), roundEngine.revision());
            return "补杠/暗杠成功，已补摸一张牌";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    public static boolean roundFinished() {
        return roundEngine != null && roundEngine.phase() == RoundPhase.FINISHED;
    }

    public static RoundResult declareLocalWin() {
        if (!canLocalWin()) throw new IllegalStateException("当前手牌尚未形成合法胡牌牌型");
        if (roundEngine.phase() == RoundPhase.WAITING_FOR_CLAIMS) {
            if (!isAwaitingLocalClaim()) throw new IllegalStateException("请等待对手弃牌停顿结束后再选择点炮胡");
            roundEngine.submitClaim(Seat.EAST, PlayerActionType.HU, roundEngine.revision());
        } else {
            roundEngine.declareSelfDraw(Seat.EAST, roundEngine.revision());
        }
        return applyOutcome(roundEngine.outcome().orElseThrow());
    }

    private static RoundResult applyOutcome(RoundOutcome outcome) {
        if (lastResult != null) return lastResult;
        Map<String, Long> changes = new LinkedHashMap<>();
        for (Seat seat : Seat.values()) {
            changes.put(players.get(seat.ordinal()).name(), outcome.scoreChanges().getOrDefault(seat, 0L));
        }
        changes.forEach((name, delta) -> {
            roomScores.merge(name, delta, Long::sum);
            friendScores.merge(name, delta, Long::sum);
        });
        String winner = outcome.winner().map(seat -> players.get(seat.ordinal()).name()).orElse("流局");
        String patterns = outcome.patterns().isEmpty() ? outcome.reason() : String.join(" · ", outcome.patterns());
        lastResult = new RoundResult(currentRound, winner, outcome.reason() + " · " + patterns, Map.copyOf(changes));
        return lastResult;
    }

    public static boolean hasNextRound() { return currentRound < totalRounds(); }

    public static void nextRound() {
        if (hasNextRound()) currentRound++;
        resetHand();
        startGame();
        lastResult = null;
    }

    public static List<ScoreRow> friendLeaderboard() {
        return friendScores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new ScoreRow(entry.getKey(), entry.getValue())).toList();
    }

    public static List<ScoreRow> roomLeaderboard() {
        return roomScores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new ScoreRow(entry.getKey(), entry.getValue())).toList();
    }

    public record PlayerState(String name, String seat, boolean owner, boolean ready, boolean connected) {}
    public record ScoreRow(String name, long score) {}
    public record RoundResult(int round, String winner, String reason, Map<String, Long> changes) {}
}

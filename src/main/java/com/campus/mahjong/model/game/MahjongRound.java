package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.rule.SettlementCalculator;
import com.campus.mahjong.model.rule.region.RegionalRuleSet;
import com.campus.mahjong.model.rule.region.RegionalRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 单局麻将的服务端权威状态机。所有修改方法均校验阶段、操作者和 revision，
 * 因而可安全地放在网络房主端串行执行。
 */
public final class MahjongRound {
    public static final long DISCARD_TIMEOUT_MILLIS = 15_000;
    private final FriendRoomSettings settings;
    private final SettlementCalculator settlementCalculator = new SettlementCalculator();
    private final RegionalRuleSet regionalRule;
    private final Deque<TileType> wall = new ArrayDeque<>();
    private final EnumMap<Seat, List<TileType>> hands = new EnumMap<>(Seat.class);
    /** 摸到但尚未通过出牌确认并入手牌的牌。 */
    private final EnumMap<Seat, TileType> drawnTiles = new EnumMap<>(Seat.class);
    private final EnumMap<Seat, List<TileType>> discards = new EnumMap<>(Seat.class);
    private final EnumMap<Seat, List<Meld>> melds = new EnumMap<>(Seat.class);
    private final EnumMap<Seat, PlayerActionType> claimResponses = new EnumMap<>(Seat.class);
    private Seat currentTurn = Seat.EAST;
    private Seat lastDiscarder;
    private TileType lastDiscard;
    private RoundPhase phase = RoundPhase.WAITING_FOR_DISCARD;
    private RoundOutcome outcome;
    private final EnumSet<Seat> winners = EnumSet.noneOf(Seat.class);
    private final List<RoundOutcome> wins = new ArrayList<>();
    private final EnumMap<Seat, TileType.Suit> missingSuits = new EnumMap<>(Seat.class);
    private long missingSuitDeadline;
    public static final long EXCHANGE_TIMEOUT_MILLIS = 15_000;
    private final EnumMap<Seat, List<TileType>> exchangeSelections = new EnumMap<>(Seat.class);
    private final EnumMap<Seat, List<TileType>> exchangeReceived = new EnumMap<>(Seat.class);
    private final Random exchangeRandom;
    private ExchangeDirection exchangeDirection;
    private long exchangeDeadline;
    private long revision;
    private long actionStartedAtMillis = System.currentTimeMillis();

    private MahjongRound(FriendRoomSettings settings, long randomSeed) {
        this.settings = settings;
        this.exchangeRandom = new Random(randomSeed ^ 0x5DEECE66DL);
        this.regionalRule = RegionalRules.resolve(settings.mode());
        for (Seat seat : Seat.values()) {
            hands.put(seat, new ArrayList<>());
            discards.put(seat, new ArrayList<>());
            melds.put(seat, new ArrayList<>());
        }
        List<TileType> shuffled = new ArrayList<>(regionalRule.buildWall());
        Collections.shuffle(shuffled, new Random(randomSeed));
        wall.addAll(shuffled);
        for (int tile = 0; tile < 13; tile++) {
            for (Seat seat : Seat.values()) hands.get(seat).add(wall.removeFirst());
        }
        sortAllHands();
        drawnTiles.put(Seat.EAST, wall.removeFirst());
        if (settings.mode() == com.campus.mahjong.model.common.MahjongTypes.ModeCode.SICHUAN
                || settings.mode() == com.campus.mahjong.model.common.MahjongTypes.ModeCode.RED_CENTER) {
            phase = RoundPhase.EXCHANGING_TILES;
            exchangeDeadline = System.currentTimeMillis() + EXCHANGE_TIMEOUT_MILLIS;
        }
    }

    public static MahjongRound start(FriendRoomSettings settings, long randomSeed) {
        return new MahjongRound(settings, randomSeed);
    }

    public Map<Seat, TileType.Suit> missingSuits() { return Map.copyOf(missingSuits); }
    public long missingSuitDeadline() { return missingSuitDeadline; }

    public long exchangeDeadline() { return exchangeDeadline; }
    public java.util.Set<Seat> exchangeReady() { return java.util.Set.copyOf(exchangeSelections.keySet()); }
    public List<TileType> exchangeSelection(Seat seat) { return exchangeSelections.getOrDefault(seat, List.of()); }
    public List<TileType> exchangeReceived(Seat seat) { return exchangeReceived.getOrDefault(seat, List.of()); }
    public Optional<ExchangeDirection> exchangeDirection() { return Optional.ofNullable(exchangeDirection); }

    public void submitExchange(Seat seat, List<TileType> tiles, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.EXCHANGING_TILES);
        if (exchangeSelections.containsKey(seat)) throw new IllegalStateException("换牌已确认，不能重复提交或修改");
        if (tiles == null || tiles.size() != 3 || tiles.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("请选择恰好三张牌");
        TileType.Suit suit = tiles.getFirst().suit();
        if (suit == TileType.Suit.HONOR || tiles.stream().anyMatch(tile -> tile.suit() != suit))
            throw new IllegalArgumentException("换三张必须选择同一种花色");
        List<TileType> available = new ArrayList<>(hand(seat));
        for (TileType tile : tiles) if (!available.remove(tile)) throw new IllegalArgumentException("所选牌张数量超过实际手牌");
        exchangeSelections.put(seat, List.copyOf(tiles));
        // 同一选择窗口的四个并发请求共享 revision，直到原子换牌完成。
        if (exchangeSelections.size() == 4) finishExchange();
    }

    public List<TileType> recommendedExchange(Seat seat) {
        List<TileType> all = hand(seat);
        TileType.Suit suit = List.of(TileType.Suit.MAN, TileType.Suit.PIN, TileType.Suit.SOU).stream()
                .filter(s -> all.stream().filter(tile -> tile.suit() == s).count() >= 3)
                .min(java.util.Comparator.comparingLong(s -> all.stream().filter(tile -> tile.suit() == s).count())).orElseThrow();
        return all.stream().filter(tile -> tile.suit() == suit).sorted().limit(3).toList();
    }

    public boolean expireExchange(long nowMillis) {
        if (phase != RoundPhase.EXCHANGING_TILES || nowMillis < exchangeDeadline) return false;
        for (Seat seat : Seat.values()) exchangeSelections.putIfAbsent(seat, recommendedExchange(seat));
        finishExchange();
        return true;
    }

    private void finishExchange() {
        exchangeDirection = ExchangeDirection.values()[exchangeRandom.nextInt(3)];
        // 必须先移除四家换出的牌，再加入收到的牌，不能沿座次传递已收到的牌。
        for (Seat seat : Seat.values()) {
            for (TileType tile : exchangeSelections.get(seat)) {
                if (!hands.get(seat).remove(tile)) drawnTiles.remove(seat);
            }
        }
        for (Seat sender : Seat.values()) {
            Seat recipient = exchangeDirection.recipient(sender);
            List<TileType> received = exchangeSelections.get(sender);
            exchangeReceived.put(recipient, received);
            hands.get(recipient).addAll(received);
        }
        // 庄家第十四张若参与换出，用收到的一张接替独立摸牌位，保持 13+1 的界面约定。
        if (!drawnTiles.containsKey(Seat.EAST)) {
            TileType replacement = exchangeReceived.get(Seat.EAST).getLast();
            hands.get(Seat.EAST).remove(replacement);
            drawnTiles.put(Seat.EAST, replacement);
        }
        sortAllHands();
        phase = RoundPhase.CHOOSING_MISSING_SUIT;
        missingSuitDeadline = System.currentTimeMillis() + 10_000;
        actionStartedAtMillis = System.currentTimeMillis();
        revision++;
    }

    public void chooseMissingSuit(Seat seat, TileType.Suit suit, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.CHOOSING_MISSING_SUIT);
        if (suit == null || suit == TileType.Suit.HONOR) throw new IllegalArgumentException("请选择万、筒或条");
        if (missingSuits.containsKey(seat)) throw new IllegalStateException("定缺后本轮不能更改");
        missingSuits.put(seat, suit);
        if (missingSuits.size() == Seat.values().length) finishMissingSuitSelection();
    }

    public boolean expireMissingSuitSelection(long nowMillis) {
        if (phase != RoundPhase.CHOOSING_MISSING_SUIT || nowMillis < missingSuitDeadline) return false;
        for (Seat seat : Seat.values()) missingSuits.putIfAbsent(seat, recommendedMissingSuit(seat));
        finishMissingSuitSelection();
        return true;
    }

    public TileType.Suit recommendedMissingSuit(Seat seat) {
        return List.of(TileType.Suit.MAN, TileType.Suit.PIN, TileType.Suit.SOU).stream()
                .min(java.util.Comparator.comparingLong(suit -> hand(seat).stream().filter(tile -> tile.suit() == suit).count()))
                .orElseThrow();
    }

    private void finishMissingSuitSelection() {
        phase = RoundPhase.WAITING_FOR_DISCARD;
        actionStartedAtMillis = System.currentTimeMillis();
        revision++;
    }

    private boolean isMissingTile(Seat seat, TileType tile) { return tile.suit() == missingSuits.get(seat); }
    public List<TileType> discardableTiles(Seat seat) {
        if (phase != RoundPhase.WAITING_FOR_DISCARD || currentTurn != seat || winners.contains(seat)) return List.of();
        List<TileType> all = hand(seat);
        boolean mustDiscardMissing = all.stream().anyMatch(tile -> isMissingTile(seat, tile));
        return all.stream().filter(tile -> !mustDiscardMissing || isMissingTile(seat, tile)).distinct().toList();
    }

    /** 只有出牌阶段使用此时限；碰杠胡响应窗口不会被当作出牌超时。 */
    public boolean expireDiscard(long nowMillis) {
        if (phase != RoundPhase.WAITING_FOR_DISCARD || nowMillis < actionStartedAtMillis + DISCARD_TIMEOUT_MILLIS) return false;
        List<TileType> allowed = discardableTiles(currentTurn);
        if (allowed.isEmpty()) return false;
        TileType drawn = drawnTiles.get(currentTurn);
        TileType choice = allowed.contains(drawn) ? drawn : allowed.getLast();
        discard(currentTurn, choice, drawn == choice, revision);
        return true;
    }

    private boolean canWin(Seat seat, List<TileType> tiles) {
        return tiles.stream().noneMatch(tile -> isMissingTile(seat, tile))
                && melds.get(seat).stream().flatMap(meld -> meld.tiles().stream()).noneMatch(tile -> isMissingTile(seat, tile))
                && regionalRule.canWin(tiles, melds.get(seat));
    }

    public java.util.Set<Seat> winners() { return java.util.Set.copyOf(winners); }
    public List<RoundOutcome> wins() { return List.copyOf(wins); }
    public RoundPhase phase() { return phase; }
    public Seat currentTurn() { return currentTurn; }
    public int wallRemaining() { return wall.size(); }
    public long revision() { return revision; }
    public long actionStartedAtMillis() { return actionStartedAtMillis; }
    public Optional<TileType> lastDiscard() { return Optional.ofNullable(lastDiscard); }
    public Optional<Seat> lastDiscarder() { return Optional.ofNullable(lastDiscarder); }
    public Optional<RoundOutcome> outcome() { return Optional.ofNullable(outcome); }
    public List<TileType> hand(Seat seat) {
        List<TileType> result = new ArrayList<>(hands.get(seat));
        Optional.ofNullable(drawnTiles.get(seat)).ifPresent(result::add);
        return List.copyOf(result);
    }
    public List<TileType> organizedHand(Seat seat) {
        return hands.get(seat).stream().sorted(java.util.Comparator
                .comparing((TileType tile) -> isMissingTile(seat, tile)).thenComparing(Enum::ordinal)).toList();
    }
    public Optional<TileType> drawnTile(Seat seat) { return Optional.ofNullable(drawnTiles.get(seat)); }
    public List<TileType> discards(Seat seat) { return List.copyOf(discards.get(seat)); }
    public List<Meld> melds(Seat seat) { return List.copyOf(melds.get(seat)); }

    public EnumSet<PlayerActionType> legalActions(Seat seat) {
        EnumSet<PlayerActionType> actions = EnumSet.noneOf(PlayerActionType.class);
        if (winners.contains(seat) || claimResponses.containsKey(seat)) return actions;
        if (phase == RoundPhase.EXCHANGING_TILES) {
            if (!exchangeSelections.containsKey(seat)) actions.add(PlayerActionType.EXCHANGE_THREE);
            return actions;
        }
        if (phase == RoundPhase.CHOOSING_MISSING_SUIT) {
            if (!missingSuits.containsKey(seat)) actions.add(PlayerActionType.DING_QUE);
            return actions;
        }
        if (phase == RoundPhase.WAITING_FOR_DISCARD && seat == currentTurn) {
            actions.add(PlayerActionType.DISCARD);
            if (canWin(seat, hand(seat))) actions.add(PlayerActionType.HU);
            if (hasConcealedGang(seat) || hasSupplementalGang(seat)) actions.add(PlayerActionType.GANG);
        } else if (phase == RoundPhase.WAITING_FOR_CLAIMS && seat != lastDiscarder) {
            if (isMissingTile(seat, lastDiscard)) return actions;
            List<TileType> candidate = withLastDiscard(seat);
            if (canWin(seat, candidate)) actions.add(PlayerActionType.HU);
            int count = count(seat, lastDiscard);
            if (count >= 2) actions.add(PlayerActionType.PENG);
            if (count >= 3) actions.add(PlayerActionType.GANG);
            if (!actions.isEmpty()) actions.add(PlayerActionType.PASS);
        }
        return actions;
    }

    public void discard(Seat seat, TileType tile, long expectedRevision) {
        discard(seat, tile, drawnTiles.get(seat) == tile, expectedRevision);
    }

    public void discard(Seat seat, TileType tile, boolean discardDrawnTile, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.WAITING_FOR_DISCARD);
        if (seat != currentTurn) throw new IllegalStateException("尚未轮到 " + seat);
        if (!discardableTiles(seat).contains(tile)) throw new IllegalArgumentException("必须优先打出定缺花色的牌");
        TileType drawn = drawnTiles.get(seat);
        if (discardDrawnTile && drawn == tile) {
            drawnTiles.remove(seat);
        } else {
            if (!hands.get(seat).remove(tile)) throw new IllegalArgumentException("手牌中没有 " + tile.displayName());
            if (drawn != null) {
                hands.get(seat).add(drawn);
                hands.get(seat).sort(Enum::compareTo);
                drawnTiles.remove(seat);
            }
        }
        discards.get(seat).add(tile);
        lastDiscard = tile;
        lastDiscarder = seat;
        claimResponses.clear();
        phase = RoundPhase.WAITING_FOR_CLAIMS;
        revision++;
        actionStartedAtMillis = System.currentTimeMillis();
        // 无合法碰杠胡的座位自动过，不要求客户端发送无意义的响应。
        for (Seat other : Seat.values()) {
            if (other != lastDiscarder && legalActions(other).isEmpty()) {
                claimResponses.put(other, PlayerActionType.PASS);
            }
        }
        if (claimResponses.size() == 3) resolveClaims();
    }

    public void submitClaim(Seat seat, PlayerActionType action, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.WAITING_FOR_CLAIMS);
        if (!legalActions(seat).contains(action) || action == PlayerActionType.DISCARD)
            throw new IllegalArgumentException("当前不能执行 " + action);
        if (claimResponses.containsKey(seat)) throw new IllegalStateException("该玩家已经响应过");
        claimResponses.put(seat, action);
        if (claimResponses.size() == 3) resolveClaims();
    }

    public void declareSelfDraw(Seat seat, long expectedRevision) {
        requireRevision(expectedRevision);
        if (!legalActions(seat).contains(PlayerActionType.HU)) throw new IllegalStateException("当前手牌不能自摸");
        requirePhase(RoundPhase.WAITING_FOR_DISCARD);
        var analysis = regionalRule.analyze(hand(seat), melds.get(seat));
        recordWin(seat, null, true, analysis.patterns(), analysis.fan());
        continueAfterWin(seat);
    }

    public void declareConcealedGang(Seat seat, TileType tile, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.WAITING_FOR_DISCARD);
        if (seat != currentTurn || isMissingTile(seat, tile) || count(seat, tile) != 4) throw new IllegalStateException("不能暗杠该牌");
        mergeDrawnTile(seat);
        removeCopies(seat, tile, 4);
        melds.get(seat).add(new Meld(PlayerActionType.GANG, List.of(tile, tile, tile, tile), seat));
        drawFor(seat);
    }

    /** 补杠：已有碰牌组，摸到或持有同牌第四张时将碰升级为杠并补摸。 */
    public void declareSupplementalGang(Seat seat, TileType tile, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.WAITING_FOR_DISCARD);
        if (seat != currentTurn || !hasSupplementalGang(seat, tile)) {
            throw new IllegalStateException("不能补杠该牌");
        }
        mergeDrawnTile(seat);
        removeCopies(seat, tile, 1);
        List<Meld> playerMelds = melds.get(seat);
        for (int index = 0; index < playerMelds.size(); index++) {
            Meld meld = playerMelds.get(index);
            if (meld.type() == PlayerActionType.PENG && meld.tiles().stream().allMatch(tile::equals)) {
                playerMelds.set(index, new Meld(PlayerActionType.GANG,
                        List.of(tile, tile, tile, tile), meld.fromSeat()));
                drawFor(seat);
                return;
            }
        }
        throw new IllegalStateException("未找到可补杠的碰牌组");
    }

    /** 本家主动杠的统一入口：优先执行补杠，否则按暗杠处理。 */
    public void declareSelfGang(Seat seat, TileType tile, long expectedRevision) {
        if (hasSupplementalGang(seat, tile)) {
            declareSupplementalGang(seat, tile, expectedRevision);
        } else {
            declareConcealedGang(seat, tile, expectedRevision);
        }
    }

    /** 当前回合可由玩家选择的暗杠/补杠牌。 */
    public List<TileType> selfGangTiles(Seat seat) {
        if (phase != RoundPhase.WAITING_FOR_DISCARD || currentTurn != seat) return List.of();
        List<TileType> candidates = new ArrayList<>();
        for (TileType tile : TileType.values()) {
            if (!isMissingTile(seat, tile) && (count(seat, tile) == 4 || hasSupplementalGang(seat, tile))) candidates.add(tile);
        }
        return List.copyOf(candidates);
    }

    /** 测试/本地演示可让尚未响应的座位自动选择过。 */
    public void passRemainingClaims() {
        requirePhase(RoundPhase.WAITING_FOR_CLAIMS);
        for (Seat seat : Seat.values()) {
            if (seat != lastDiscarder && !claimResponses.containsKey(seat)) claimResponses.put(seat, PlayerActionType.PASS);
        }
        resolveClaims();
    }

    private void resolveClaims() {
        Seat lastWinner = null;
        for (int distance = 1; distance <= 3; distance++) {
            Seat seat = advance(lastDiscarder, distance);
            if (claimResponses.get(seat) == PlayerActionType.HU) {
                var analysis = regionalRule.analyze(withLastDiscard(seat), melds.get(seat));
                recordWin(seat, lastDiscarder, false, analysis.patterns(), analysis.fan());
                lastWinner = seat;
            }
        }
        // 点炮胡后由胡牌玩家的下一家继续；一炮多响时以座次处理的最后一位胡牌者为基准。
        if (lastWinner != null) { continueAfterWin(lastWinner); return; }
        Seat claimant;
        claimant = firstClaimant(PlayerActionType.GANG);
        if (claimant != null) { applyExposedSet(claimant, PlayerActionType.GANG, 3); return; }
        claimant = firstClaimant(PlayerActionType.PENG);
        if (claimant != null) { applyExposedSet(claimant, PlayerActionType.PENG, 2); return; }
        currentTurn = nextSeat(lastDiscarder);
        drawFor(currentTurn);
    }

    private Seat firstClaimant(PlayerActionType action) {
        for (int distance = 1; distance <= 3; distance++) {
            Seat seat = advance(lastDiscarder, distance);
            if (claimResponses.get(seat) == action) return seat;
        }
        return null;
    }

    private void applyExposedSet(Seat claimant, PlayerActionType type, int handCopies) {
        removeCopies(claimant, lastDiscard, handCopies);
        List<TileType> tiles = new ArrayList<>();
        for (int index = 0; index < handCopies + 1; index++) tiles.add(lastDiscard);
        melds.get(claimant).add(new Meld(type, tiles, lastDiscarder));
        removeLastDiscardFromTable();
        currentTurn = claimant;
        phase = RoundPhase.WAITING_FOR_DISCARD;
        claimResponses.clear();
        if (type == PlayerActionType.GANG) drawFor(claimant);
        else {
            revision++;
            actionStartedAtMillis = System.currentTimeMillis();
        }
    }

    private void drawFor(Seat seat) {
        if (wall.isEmpty()) { finishRound("牌墙耗尽"); return; }
        if (drawnTiles.containsKey(seat)) throw new IllegalStateException("尚有未处理的摸牌");
        drawnTiles.put(seat, wall.removeFirst());
        phase = RoundPhase.WAITING_FOR_DISCARD;
        claimResponses.clear();
        revision++;
        actionStartedAtMillis = System.currentTimeMillis();
    }

    private void recordWin(Seat winner, Seat supplier, boolean selfDraw, List<String> patterns, int fan) {
        EnumSet<Seat> active = EnumSet.allOf(Seat.class);
        active.removeAll(winners);
        wins.add(settlementCalculator.win(winner, supplier, selfDraw, settings, patterns, fan, active));
        winners.add(winner);
        winDetails.add(new WinEvent(winDetails.size() + 1, 0, winner,
                selfDraw ? drawnTiles.getOrDefault(winner, hand(winner).getLast()) : lastDiscard,
                selfDraw, fan, patterns, wins.getLast().scoreChanges()));
    }

    private final List<WinEvent> winDetails = new ArrayList<>();
    public List<WinEvent> winDetails() { return List.copyOf(winDetails); }

    private void continueAfterWin(Seat previous) {
        claimResponses.clear();
        if (winners.size() >= 3) { finishRound("三名玩家已胡牌"); return; }
        currentTurn = nextSeat(previous);
        drawFor(currentTurn);
    }

    private void finishRound(String reason) {
        EnumMap<Seat, Long> totals = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) totals.put(seat, 0L);
        wins.forEach(win -> win.scoreChanges().forEach((seat, delta) -> totals.merge(seat, delta, Long::sum)));
        outcome = new RoundOutcome(Optional.empty(), Optional.empty(), false, reason, List.of(), Map.copyOf(totals));
        phase = RoundPhase.FINISHED;
        claimResponses.clear();
        revision++;
        actionStartedAtMillis = System.currentTimeMillis();
    }

    private void removeLastDiscardFromTable() {
        List<TileType> pile = discards.get(lastDiscarder);
        pile.remove(pile.size() - 1);
    }

    private List<TileType> withLastDiscard(Seat seat) {
        List<TileType> candidate = new ArrayList<>(hand(seat));
        candidate.add(lastDiscard);
        return candidate;
    }

    private boolean hasConcealedGang(Seat seat) {
        for (TileType tile : TileType.values()) if (!isMissingTile(seat, tile) && count(seat, tile) == 4) return true;
        return false;
    }

    private boolean hasSupplementalGang(Seat seat) {
        for (TileType tile : TileType.values()) if (hasSupplementalGang(seat, tile)) return true;
        return false;
    }

    private boolean hasSupplementalGang(Seat seat, TileType tile) {
        return !isMissingTile(seat, tile) && count(seat, tile) >= 1 && melds.get(seat).stream().anyMatch(meld ->
                meld.type() == PlayerActionType.PENG && meld.tiles().stream().allMatch(tile::equals));
    }

    private int count(Seat seat, TileType tile) {
        return (int) hand(seat).stream().filter(tile::equals).count();
    }

    private void mergeDrawnTile(Seat seat) {
        TileType drawn = drawnTiles.remove(seat);
        if (drawn != null) {
            hands.get(seat).add(drawn);
            hands.get(seat).sort(Enum::compareTo);
        }
    }

    private void removeCopies(Seat seat, TileType tile, int amount) {
        for (int index = 0; index < amount; index++) {
            if (hands.get(seat).remove(tile)) continue;
            if (drawnTiles.get(seat) == tile) {
                drawnTiles.remove(seat);
                continue;
            }
            throw new IllegalStateException("牌数不足");
        }
    }

    private void requireRevision(long expected) {
        if (expected != revision) throw new IllegalStateException("牌局状态已更新，请刷新后重试");
    }

    private void requirePhase(RoundPhase expected) {
        if (phase != expected) throw new IllegalStateException("当前阶段不能执行该操作: " + phase);
    }

    private void sortAllHands() { hands.values().forEach(hand -> hand.sort(Enum::compareTo)); }
    private Seat nextSeat(Seat seat) {
        for (int distance = 1; distance <= 4; distance++) {
            Seat next = advance(seat, distance);
            if (!winners.contains(next)) return next;
        }
        throw new IllegalStateException("没有可继续操作的玩家");
    }
    private Seat advance(Seat seat, int distance) {
        Seat[] seats = Seat.values();
        return seats[(seat.ordinal() + distance) % seats.length];
    }
}

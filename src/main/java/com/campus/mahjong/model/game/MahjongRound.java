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
    private long revision;

    private MahjongRound(FriendRoomSettings settings, long randomSeed) {
        this.settings = settings;
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
    }

    public static MahjongRound start(FriendRoomSettings settings, long randomSeed) {
        return new MahjongRound(settings, randomSeed);
    }

    public RoundPhase phase() { return phase; }
    public Seat currentTurn() { return currentTurn; }
    public int wallRemaining() { return wall.size(); }
    public long revision() { return revision; }
    public Optional<TileType> lastDiscard() { return Optional.ofNullable(lastDiscard); }
    public Optional<Seat> lastDiscarder() { return Optional.ofNullable(lastDiscarder); }
    public Optional<RoundOutcome> outcome() { return Optional.ofNullable(outcome); }
    public List<TileType> hand(Seat seat) {
        List<TileType> result = new ArrayList<>(hands.get(seat));
        Optional.ofNullable(drawnTiles.get(seat)).ifPresent(result::add);
        return List.copyOf(result);
    }
    public List<TileType> organizedHand(Seat seat) { return List.copyOf(hands.get(seat)); }
    public Optional<TileType> drawnTile(Seat seat) { return Optional.ofNullable(drawnTiles.get(seat)); }
    public List<TileType> discards(Seat seat) { return List.copyOf(discards.get(seat)); }
    public List<Meld> melds(Seat seat) { return List.copyOf(melds.get(seat)); }

    public EnumSet<PlayerActionType> legalActions(Seat seat) {
        EnumSet<PlayerActionType> actions = EnumSet.noneOf(PlayerActionType.class);
        if (claimResponses.containsKey(seat)) return actions;
        if (phase == RoundPhase.WAITING_FOR_DISCARD && seat == currentTurn) {
            actions.add(PlayerActionType.DISCARD);
            if (regionalRule.canWin(hand(seat), melds.get(seat))) actions.add(PlayerActionType.HU);
            if (hasConcealedGang(seat) || hasSupplementalGang(seat)) actions.add(PlayerActionType.GANG);
        } else if (phase == RoundPhase.WAITING_FOR_CLAIMS && seat != lastDiscarder) {
            actions.add(PlayerActionType.PASS);
            List<TileType> candidate = withLastDiscard(seat);
            if (regionalRule.canWin(candidate, melds.get(seat))) actions.add(PlayerActionType.HU);
            int count = count(seat, lastDiscard);
            if (count >= 2) actions.add(PlayerActionType.PENG);
            if (count >= 3) actions.add(PlayerActionType.GANG);
            if (regionalRule.allowChi() && seat == nextSeat(lastDiscarder) && findChiTiles(seat).isPresent()) actions.add(PlayerActionType.CHI);
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
        var analysis = regionalRule.analyze(hand(seat), melds.get(seat));
        finish(settlementCalculator.win(seat, null, true, settings, analysis.patterns(), analysis.fan()));
    }

    public void declareConcealedGang(Seat seat, TileType tile, long expectedRevision) {
        requireRevision(expectedRevision);
        requirePhase(RoundPhase.WAITING_FOR_DISCARD);
        if (seat != currentTurn || count(seat, tile) != 4) throw new IllegalStateException("不能暗杠该牌");
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
            if (count(seat, tile) == 4 || hasSupplementalGang(seat, tile)) candidates.add(tile);
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
        Seat claimant = firstClaimant(PlayerActionType.HU);
        if (claimant != null) {
            var analysis = regionalRule.analyze(withLastDiscard(claimant), melds.get(claimant));
            finish(settlementCalculator.win(claimant, lastDiscarder, false, settings, analysis.patterns(), analysis.fan()));
            return;
        }
        claimant = firstClaimant(PlayerActionType.GANG);
        if (claimant != null) { applyExposedSet(claimant, PlayerActionType.GANG, 3); return; }
        claimant = firstClaimant(PlayerActionType.PENG);
        if (claimant != null) { applyExposedSet(claimant, PlayerActionType.PENG, 2); return; }
        claimant = firstClaimant(PlayerActionType.CHI);
        if (claimant != null) { applyChi(claimant); return; }
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
        else revision++;
    }

    private void applyChi(Seat claimant) {
        List<TileType> pair = findChiTiles(claimant).orElseThrow();
        pair.forEach(tile -> hands.get(claimant).remove(tile));
        List<TileType> sequence = new ArrayList<>(pair);
        sequence.add(lastDiscard);
        sequence.sort(Enum::compareTo);
        melds.get(claimant).add(new Meld(PlayerActionType.CHI, sequence, lastDiscarder));
        removeLastDiscardFromTable();
        currentTurn = claimant;
        phase = RoundPhase.WAITING_FOR_DISCARD;
        claimResponses.clear();
        revision++;
    }

    private Optional<List<TileType>> findChiTiles(Seat seat) {
        if (lastDiscard == null || !lastDiscard.suited()) return Optional.empty();
        int ordinal = lastDiscard.ordinal();
        int[][] offsets = {{-2, -1}, {-1, 1}, {1, 2}};
        for (int[] pair : offsets) {
            int left = ordinal + pair[0], right = ordinal + pair[1];
            if (left < 0 || right >= TileType.values().length) continue;
            TileType a = TileType.values()[left], b = TileType.values()[right];
            if (a.suit() == lastDiscard.suit() && b.suit() == lastDiscard.suit()
                    && hands.get(seat).contains(a) && hands.get(seat).contains(b)) return Optional.of(List.of(a, b));
        }
        return Optional.empty();
    }

    private void drawFor(Seat seat) {
        if (wall.isEmpty()) { finish(settlementCalculator.draw()); return; }
        if (drawnTiles.containsKey(seat)) throw new IllegalStateException("尚有未处理的摸牌");
        drawnTiles.put(seat, wall.removeFirst());
        phase = RoundPhase.WAITING_FOR_DISCARD;
        claimResponses.clear();
        revision++;
    }

    private void finish(RoundOutcome result) {
        outcome = result;
        phase = RoundPhase.FINISHED;
        claimResponses.clear();
        revision++;
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
        for (TileType tile : TileType.values()) if (count(seat, tile) == 4) return true;
        return false;
    }

    private boolean hasSupplementalGang(Seat seat) {
        for (TileType tile : TileType.values()) if (hasSupplementalGang(seat, tile)) return true;
        return false;
    }

    private boolean hasSupplementalGang(Seat seat, TileType tile) {
        return count(seat, tile) >= 1 && melds.get(seat).stream().anyMatch(meld ->
                meld.type() == PlayerActionType.PENG && meld.tiles().stream().allMatch(tile::equals));
    }

    private int count(Seat seat, TileType tile) {
        return (int) hand(seat).stream().filter(tile::equals).count();
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
    private Seat nextSeat(Seat seat) { return advance(seat, 1); }
    private Seat advance(Seat seat, int distance) {
        Seat[] seats = Seat.values();
        return seats[(seat.ordinal() + distance) % seats.length];
    }
}

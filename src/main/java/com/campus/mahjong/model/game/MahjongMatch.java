package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.util.*;

/** 整场权威状态：收集流水、累计分数，并在单轮结束后原子地自动发下一轮。 */
public final class MahjongMatch {
    private final FriendRoomSettings settings;
    private final long seed;
    private MahjongRound round;
    private int roundNumber;
    private int recordedWins;
    private int recordedGangs;
    private boolean finished;
    private long revisionOffset;
    private final List<ScoreEntry> ledger = new ArrayList<>();
    private final List<WinEvent> winEvents = new ArrayList<>();
    public List<WinEvent> winEvents() { return List.copyOf(winEvents); }
    private final EnumMap<Seat, Long> scores = new EnumMap<>(Seat.class);

    public MahjongMatch(FriendRoomSettings settings, long seed, int firstRound) {
        this.settings = settings;
        this.seed = seed;
        this.roundNumber = firstRound;
        this.round = MahjongRound.start(settings, seed);
        for (Seat seat : Seat.values()) scores.put(seat, 0L);
    }

    public MahjongRound round() { return round; }
    public int roundNumber() { return roundNumber; }
    public int totalRounds() { return settings.rounds(); }
    public boolean finished() { return finished; }
    public long revision() { return revisionOffset + round.revision(); }
    public List<ScoreEntry> ledger() { return List.copyOf(ledger); }
    public Map<Seat, Long> scores() { return Map.copyOf(scores); }

    public boolean expireTimedActions(long nowMillis) {
        if (finished) return false;
        boolean changed = round.expireExchange(nowMillis) || round.expireMissingSuitSelection(nowMillis) || round.expireDiscard(nowMillis);
        if (changed) synchronizeRound();
        return changed;
    }

    public void apply(Seat seat, PlayerActionType action, TileType tile, long expectedRevision) {
        if (finished) throw new IllegalStateException("所有轮次已结束");
        if (expectedRevision != revision()) throw new IllegalStateException("牌局状态已更新，请刷新后重试");
        long revision = round.revision();
        switch (action) {
            case DING_QUE -> round.chooseMissingSuit(seat, Objects.requireNonNull(tile, "请选择定缺花色").suit(), revision);
            case DISCARD -> round.discard(seat, Objects.requireNonNull(tile, "请选择出牌"), revision);
            case HU -> {
                if (round.phase() == RoundPhase.WAITING_FOR_DISCARD) round.declareSelfDraw(seat, revision);
                else round.submitClaim(seat, action, revision);
            }
            case GANG -> {
                if (round.phase() == RoundPhase.WAITING_FOR_DISCARD)
                    round.declareSelfGang(seat, Objects.requireNonNull(tile, "请选择杠牌"), revision);
                else round.submitClaim(seat, action, revision);
            }
            case PASS, PENG -> round.submitClaim(seat, action, revision);
            default -> throw new IllegalArgumentException("不支持的操作：" + action);
        }
        synchronizeRound();
    }

    public void exchange(Seat seat, List<TileType> tiles, long expectedRevision) {
        if (finished || expectedRevision != revision()) throw new IllegalStateException("牌局状态已更新，请刷新后重试");
        round.submitExchange(seat, tiles, round.revision());
    }

    /** 本地演示也通过同一入口累计流水与推进轮次。 */
    public void synchronizeRound() {
        if (finished) return;
        round.expireMissingSuitSelection(System.currentTimeMillis());
        List<RoundOutcome> gangs = round.gangSettlements();
        while (recordedGangs < gangs.size()) recordSettlement(gangs.get(recordedGangs++));
        List<RoundOutcome> wins = round.wins();
        while (recordedWins < wins.size()) {
            RoundOutcome win = wins.get(recordedWins++);
            WinEvent detail = round.winDetails().get(recordedWins - 1);
            winEvents.add(new WinEvent(winEvents.size() + 1, roundNumber, detail.winner(), detail.tile(),
                    detail.selfDraw(), detail.fan(), detail.patterns(), detail.scoreChanges()));
            Seat winner = win.winner().orElseThrow();
            for (Seat payer : Seat.values()) {
                long change = win.scoreChanges().getOrDefault(payer, 0L);
                if (change < 0) ledger.add(new ScoreEntry(ledger.size() + 1, roundNumber,
                        Optional.of(payer), Optional.of(winner), -change, win.reason(),
                        java.util.stream.Stream.concat(win.patterns().stream(), java.util.stream.Stream.of(detail.fan() + "番")).toList()));
            }
            win.scoreChanges().forEach((seat, delta) -> scores.merge(seat, delta, Long::sum));
        }
        if (round.phase() != RoundPhase.FINISHED) return;
        ledger.add(new ScoreEntry(ledger.size() + 1, roundNumber, Optional.empty(), Optional.empty(),
                0, round.outcome().orElseThrow().reason(), List.of()));
        if (roundNumber >= settings.rounds()) { finished = true; return; }
        revisionOffset = revision() + 1;
        roundNumber++;
        round = MahjongRound.start(settings, seed + roundNumber - 1);
        recordedWins = 0;
        recordedGangs = 0;
    }

    private void recordSettlement(RoundOutcome settlement) {
        Seat payee = settlement.winner().orElseThrow();
        for (Seat payer : Seat.values()) {
            long change = settlement.scoreChanges().getOrDefault(payer, 0L);
            if (change < 0) ledger.add(new ScoreEntry(ledger.size() + 1, roundNumber,
                    Optional.of(payer), Optional.of(payee), -change, settlement.reason(), settlement.patterns()));
        }
        settlement.scoreChanges().forEach((seat, delta) -> scores.merge(seat, delta, Long::sum));
    }
}

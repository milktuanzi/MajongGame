package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.game.RoundOutcome;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 统一完成零和计分、基础倍率及封顶处理。 */
public final class SettlementCalculator {
    public RoundOutcome draw() {
        EnumMap<Seat, Long> changes = zeroChanges();
        return new RoundOutcome(Optional.empty(), Optional.empty(), false,
                "牌墙耗尽，本局流局", List.of("流局"), changes);
    }

    public RoundOutcome win(Seat winner, Seat supplier, boolean selfDraw,
                            FriendRoomSettings settings, List<String> patterns, int fan) {
        return win(winner, supplier, selfDraw, settings, patterns, fan, java.util.EnumSet.allOf(Seat.class));
    }

    public RoundOutcome win(Seat winner, Seat supplier, boolean selfDraw,
                            FriendRoomSettings settings, List<String> patterns, int fan, Set<Seat> activeSeats) {
        if (fan < 1) throw new IllegalArgumentException("fan must be positive");
        long basePayment = Math.multiplyExact(settings.baseMultiplier(), 8L * fan);
        long cap = settings.scoreCap().map(Integer::longValue).orElse(Long.MAX_VALUE);
        EnumMap<Seat, Long> changes = zeroChanges();
        if (selfDraw) {
            long payment = Math.min(basePayment, cap);
            for (Seat seat : Seat.values()) {
                if (seat == winner || !activeSeats.contains(seat)) continue;
                changes.put(seat, -payment);
                changes.merge(winner, payment, Long::sum);
            }
        } else {
            long payment = Math.min(basePayment * 3L, cap);
            changes.put(supplier, -payment);
            changes.put(winner, payment);
        }
        return new RoundOutcome(Optional.of(winner), Optional.ofNullable(supplier), selfDraw,
                selfDraw ? "自摸" : "点炮", patterns, changes);
    }

    private EnumMap<Seat, Long> zeroChanges() {
        EnumMap<Seat, Long> changes = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) changes.put(seat, 0L);
        return changes;
    }
}

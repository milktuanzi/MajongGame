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
        if (fan < 0 || fan > 30) throw new IllegalArgumentException("fan must be between 0 and 30");
        long basePayment = Math.multiplyExact(settings.baseMultiplier(), 10L * (1L << fan));
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
            long payment = Math.min(basePayment, cap);
            changes.put(supplier, -payment);
            changes.put(winner, payment);
        }
        return new RoundOutcome(Optional.of(winner), Optional.ofNullable(supplier), selfDraw,
                selfDraw ? "自摸" : "点炮", patterns, changes);
    }

    /** 杠牌即时结算：点杠由点杠者单付，暗杠/补杠由其余仍在局玩家各付。 */
    public RoundOutcome gang(Seat seat, Seat supplier, String kind, int fan,
                             FriendRoomSettings settings, Set<Seat> activeSeats) {
        long payment = Math.min(Math.multiplyExact(settings.baseMultiplier(), 10L * (1L << fan)),
                settings.scoreCap().map(Integer::longValue).orElse(Long.MAX_VALUE));
        EnumMap<Seat, Long> changes = zeroChanges();
        if (supplier != null) {
            changes.put(supplier, -payment); changes.put(seat, payment);
        } else {
            for (Seat payer : Seat.values()) if (payer != seat && activeSeats.contains(payer)) {
                changes.put(payer, -payment); changes.merge(seat, payment, Long::sum);
            }
        }
        return new RoundOutcome(Optional.of(seat), Optional.ofNullable(supplier), false, kind,
                List.of(kind, fan + "番"), changes);
    }

    private EnumMap<Seat, Long> zeroChanges() {
        EnumMap<Seat, Long> changes = new EnumMap<>(Seat.class);
        for (Seat seat : Seat.values()) changes.put(seat, 0L);
        return changes;
    }
}

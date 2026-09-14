package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.*;
import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmedFanTableTest {
    private final HandPatternAnalyzer analyzer = new HandPatternAnalyzer();
    private List<TileType> tiles(String man, String pin) {
        var result = new ArrayList<TileType>();
        man.chars().forEach(c -> result.add(TileType.values()[c - '1']));
        pin.chars().forEach(c -> result.add(TileType.values()[9 + c - '1']));
        return result;
    }
    private List<Meld> melds(String man, String pin, boolean gang) {
        return tiles(man, pin).stream().map(t -> new Meld(gang ? PlayerActionType.GANG : PlayerActionType.PENG,
                Collections.nCopies(gang ? 4 : 3, t), Seat.SOUTH)).toList();
    }
    private void check(String name, int fan, String man, String pin, List<Meld> melds) {
        var result = analyzer.analyze(tiles(man, pin), melds);
        assertEquals(List.of(name), result.patterns()); assertEquals(fan, result.fan(), name);
    }
    @Test void allConfirmedConcealedPatternsHaveExactTotalFans() {
        check("平胡", 0, "12345699", "234789", List.of());
        check("清一色", 2, "12323445678955", "", List.of());
        check("碰碰胡", 1, "111444", "33366688", List.of());
        check("七对", 2, "112244", "33557799", List.of());
        check("龙七对", 3, "111122", "33557799", List.of());
        check("双龙七对", 4, "11112222", "335599", List.of());
        check("清七对", 4, "11223344557799", "", List.of());
        check("将对", 3, "222555", "22255588", List.of());
        check("幺九", 2, "12378911", "123789", List.of());
        check("清龙七对", 5, "11112233557799", "", List.of());
        check("清双龙七对", 6, "11112222335599", "", List.of());
    }
    @Test void exposedSetsDetermineGoldenHookAndEighteenArhats() {
        check("金钩钓", 2, "99", "", melds("14", "36", false));
        check("清金钩钓", 4, "99", "", melds("1346", "", false));
        check("十八罗汉", 6, "99", "", melds("14", "36", true));
        check("将十八罗汉", 7, "88", "", melds("25", "25", true));
        var mixed = new ArrayList<>(melds("14", "36", true));
        mixed.set(0, melds("1", "", false).getFirst());
        check("金钩钓", 2, "99", "", mixed);
    }
    @Test void terminalsMustAppearInEverySetAndThePair() {
        check("平胡", 0, "12378955", "123789", List.of());
        check("平胡", 0, "12345611", "123789", List.of());
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(tiles("123", ""), List.of()));
    }
    @Test void zeroFanAndDoublingUseTenPointBaseAndOnlyOneDiscardPayment() {
        var settings = new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.empty(), 4, false, "");
        var calculator = new SettlementCalculator();
        for (int fan = 0; fan <= 7; fan++) {
            long share = 20L << fan;
            var result = calculator.win(Seat.EAST, Seat.WEST, false, settings, List.of("测试"), fan);
            assertEquals(share, result.scoreChanges().get(Seat.EAST));
            assertEquals(-share, result.scoreChanges().get(Seat.WEST));
            assertEquals(0L, result.scoreChanges().get(Seat.SOUTH));
        }
        var self = calculator.win(Seat.SOUTH, null, true, settings, List.of("平胡"), 0,
                EnumSet.of(Seat.SOUTH, Seat.WEST, Seat.NORTH));
        assertEquals(40L, self.scoreChanges().get(Seat.SOUTH));
        assertEquals(0L, self.scoreChanges().get(Seat.EAST));
        assertEquals(-20L, self.scoreChanges().get(Seat.WEST));
        assertEquals(0L, self.scoreChanges().values().stream().mapToLong(Long::longValue).sum());
    }
}

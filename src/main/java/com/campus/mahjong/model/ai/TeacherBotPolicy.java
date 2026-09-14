package com.campus.mahjong.model.ai;

import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.game.TileType;
import java.util.*;

/** 只接收本人快照。第一版使用确定性结构权重；不声称最优、向听或胜率。 */
public final class TeacherBotPolicy {
    public record Decision(PlayerActionType type, List<Tile> tiles, String reason,
                           List<RuleKnowledgeBase.Citation> citations) {
        public Decision { tiles = List.copyOf(tiles); citations = List.copyOf(citations); }
        public ActionRequest request(GameSnapshot state, PlayerId id) {
            return new ActionRequest(state.gameId(), id, type, tiles, state.revision());
        }
    }
    private final RuleKnowledgeBase knowledge;
    public TeacherBotPolicy(RuleKnowledgeBase knowledge) { this.knowledge = knowledge; }
    public Optional<Decision> choose(ModeCode mode, Seat seat, GameSnapshot state) {
        if (state.status() == GameStatus.FINISHED || state.winners().contains(seat)) return Optional.empty();
        List<TileType> hand = new ArrayList<>();
        state.ownHand().forEach(t -> hand.add(TileType.fromDisplayName(t.code())));
        state.drawnTile().ifPresent(t -> hand.add(TileType.fromDisplayName(t.code())));
        Set<PlayerActionType> actions = new HashSet<>();
        state.availableActions().forEach(a -> actions.add(a.type()));
        if (actions.contains(PlayerActionType.HU)) return Optional.of(decision(mode, PlayerActionType.HU, List.of(), "服务端已确认可以胡牌。", "HU"));
        if (actions.contains(PlayerActionType.EXCHANGE_THREE)) {
            var suit = numberedSuits().stream().filter(s -> hand.stream().filter(t -> t.suit() == s).count() >= 3)
                    .min(Comparator.comparingLong(s -> hand.stream().filter(t -> t.suit() == s).count())).orElseThrow();
            var selected = hand.stream().filter(t -> t.suit() == suit).sorted().limit(3).toList();
            return Optional.of(decision(mode, PlayerActionType.EXCHANGE_THREE, selected, "选择张数较少的可交换花色，交出三张同花色牌。", "EXCHANGE"));
        }
        if (actions.contains(PlayerActionType.DING_QUE)) {
            var suit = numberedSuits().stream().min(Comparator.comparingLong(s -> hand.stream().filter(t -> t.suit() == s).count())).orElseThrow();
            var tile = Arrays.stream(TileType.values()).filter(t -> t.suit() == suit).findFirst().orElseThrow();
            return Optional.of(decision(mode, PlayerActionType.DING_QUE, List.of(tile), "选择手牌张数最少的花色作为缺门。", "MISSING"));
        }
        if (actions.contains(PlayerActionType.DISCARD)) {
            var allowed = state.availableActions().stream().filter(a -> a.type() == PlayerActionType.DISCARD).findFirst().orElseThrow()
                    .relatedTiles().stream().map(t -> TileType.fromDisplayName(t.code())).distinct().toList();
            if (allowed.isEmpty()) return Optional.empty();
            TileType selected = allowed.stream().min(Comparator.comparingInt((TileType t) -> keepValue(hand, t, mode)).thenComparingInt(Enum::ordinal)).orElseThrow();
            boolean forced = selected.suit() == state.missingSuits().get(seat);
            String reason = forced ? "先打出缺门牌，这是定缺规则要求；在合法候选中选择结构保留价值较低的一张。"
                    : "这张牌在合法候选中的结构保留权重最低，优先留下对子、刻子和相邻牌。相同权重按牌序选择。";
            reason += " 这是初级启发式选择，不代表唯一最优，也没有读取其他玩家暗牌或牌墙顺序。";
            return Optional.of(decision(mode, PlayerActionType.DISCARD, List.of(selected), reason, "DISCARD"));
        }
        if (actions.contains(PlayerActionType.PASS)) return Optional.of(decision(mode, PlayerActionType.PASS, List.of(), "初级老师暂不主动碰杠，本次选择过；不表示碰杠不合法。", "PASS"));
        return Optional.empty();
    }
    private Decision decision(ModeCode mode, PlayerActionType type, List<TileType> tiles, String reason, String topic) {
        return new Decision(type, tiles.stream().map(t -> new Tile(t.displayName())).toList(), reason, knowledge.retrieve(mode, Set.of(topic)));
    }
    private List<TileType.Suit> numberedSuits() { return List.of(TileType.Suit.MAN, TileType.Suit.PIN, TileType.Suit.SOU); }
    static int keepValue(List<TileType> hand, TileType tile, ModeCode mode) {
        if (mode == ModeCode.RED_CENTER && tile == TileType.RED) return 100;
        int value = (Collections.frequency(hand, tile) - 1) * 8;
        if (!tile.suited()) return value;
        for (int distance : new int[]{1,2}) for (int direction : new int[]{-1,1}) {
            int rank = tile.rank() + distance * direction;
            if (rank < 1 || rank > 9) continue;
            if (hand.stream().anyMatch(t -> t.suit() == tile.suit() && t.rank() == rank)) value += distance == 1 ? 4 : 2;
        }
        return value;
    }
}

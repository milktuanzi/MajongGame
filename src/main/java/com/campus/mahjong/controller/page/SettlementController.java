package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.game.ScoreEntry;
import com.campus.mahjong.model.repository.GameRecordRepository.PlayerRoundScore;
import com.campus.mahjong.model.session.DemoSession;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Function;

/** 全部轮次完成后展示总排名和服务器确认的逐笔流水。 */
public final class SettlementController {
    @FXML private Label resultTitle;
    @FXML private Label resultSubtitle;
    @FXML private Label progressLabel;
    @FXML private Label saveStatus;
    @FXML private VBox roundRows;
    @FXML private TableView<ScoreEntry> ledgerTable;
    private final EnumMap<Seat, String> names = new EnumMap<>(Seat.class);
    private final EnumMap<Seat, PlayerId> ids = new EnumMap<>(Seat.class);
    private Map<Seat, Long> totals;
    private List<ScoreEntry> ledger;

    @FXML private void initialize() {
        Optional<LanSession> session = LanSession.current();
        String matchId;
        String roomCode;
        ModeCode mode;
        int totalRounds;
        if (session.isPresent() && session.get().currentGame().isPresent()) {
            var lan = session.get();
            var game = lan.currentGame().orElseThrow();
            if (game.status() != GameStatus.FINISHED) throw new IllegalStateException("全部轮次尚未结束");
            var room = lan.currentRoom().orElseThrow();
            room.players().forEach(player -> { names.put(player.seat(), player.profile().nickname()); ids.put(player.seat(), player.profile().id()); });
            EnumMap<Seat, Long> scores = new EnumMap<>(Seat.class);
            game.players().forEach(player -> scores.put(player.seat(), player.score()));
            totals = Map.copyOf(scores);
            ledger = game.ledger();
            matchId = game.gameId().value(); roomCode = room.roomId().value();
            mode = room.mode(); totalRounds = room.settings().orElseThrow().rounds();
        } else {
            if (!DemoSession.roundFinished()) throw new IllegalStateException("全部轮次尚未结束");
            EnumMap<Seat, Long> scores = new EnumMap<>(Seat.class);
            for (Seat seat : Seat.values()) {
                names.put(seat, DemoSession.playerName(seat));
                ids.put(seat, DemoSession.playerId(names.get(seat)));
                scores.put(seat, DemoSession.roomScores().getOrDefault(names.get(seat), 0L));
            }
            totals = Map.copyOf(scores); ledger = DemoSession.ledger();
            matchId = DemoSession.matchId(); roomCode = DemoSession.roomCode;
            mode = DemoSession.mode; totalRounds = DemoSession.totalRounds();
        }
        resultTitle.setText("本场游戏结算");
        long transactions = ledger.stream().filter(entry -> entry.payer().isPresent()).count();
        resultSubtitle.setText("全部 " + totalRounds + " 轮已完成 · " + transactions + " 笔积分流水");
        progressLabel.setText("已完成 " + totalRounds + " / " + totalRounds + " 轮");
        renderRanking();
        column("轮次", 60, entry -> "第 " + entry.round() + " 轮");
        column("付款方", 110, entry -> entry.payer().map(names::get).orElse("—"));
        column("收款方", 110, entry -> entry.payee().map(names::get).orElse("—"));
        column("事项", 160, ScoreEntry::reason);
        column("牌型", 230, entry -> String.join(" · ", entry.patterns()));
        column("积分", 80, entry -> entry.amount() == 0 ? "—" : "+" + entry.amount());
        ledgerTable.getItems().setAll(ledger);
        ledgerTable.setPlaceholder(new Label("本场没有积分流水"));
        persist(matchId, roomCode, mode, totalRounds);
    }

    private void column(String title, double width, Function<ScoreEntry, String> value) {
        TableColumn<ScoreEntry, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        ledgerTable.getColumns().add(column);
    }

    private void renderRanking() {
        List<Seat> ranking = Arrays.stream(Seat.values()).sorted(Comparator.comparingLong((Seat seat) -> totals.get(seat)).reversed()).toList();
        for (int index = 0; index < ranking.size(); index++) {
            Seat seat = ranking.get(index);
            HBox row = new HBox(14); row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add(index == 0 ? "settlement-winner" : "settlement-row");
            Label rank = new Label(String.valueOf(index + 1)); rank.getStyleClass().add("rank-number");
            Label name = new Label(names.get(seat)); name.getStyleClass().add("settlement-name");
            Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
            long score = totals.get(seat);
            Label delta = new Label((score > 0 ? "+" : "") + score);
            delta.getStyleClass().add(score >= 0 ? "score-positive" : "score-negative");
            row.getChildren().addAll(rank, name, space, delta); roundRows.getChildren().add(row);
        }
    }

    private void persist(String matchId, String roomCode, ModeCode mode, int rounds) {
        if (LanSession.current().flatMap(LanSession::currentRoom)
                .map(room -> room.settings().map(FriendRoomSettings::teachingMode).orElse(false)
                        || room.players().stream().anyMatch(RoomPlayer::bot)).orElse(false)) {
            saveStatus.setText("教学练习场：本场排名与流水可查看，不计入好友长期积分。");
            return;
        }
        try {
            for (int round = 1; round <= rounds; round++) {
                int number = round;
                EnumMap<Seat, Long> changes = new EnumMap<>(Seat.class);
                for (Seat seat : Seat.values()) changes.put(seat, 0L);
                ledger.stream().filter(entry -> entry.round() == number).forEach(entry -> {
                    entry.payer().ifPresent(seat -> changes.merge(seat, -entry.amount(), Long::sum));
                    entry.payee().ifPresent(seat -> changes.merge(seat, entry.amount(), Long::sum));
                });
                Map<PlayerId, PlayerRoundScore> scores = new LinkedHashMap<>();
                changes.forEach((seat, delta) -> scores.put(ids.get(seat), new PlayerRoundScore(names.get(seat), delta)));
                Set<PlayerId> winners = ledger.stream().filter(entry -> entry.round() == number)
                        .flatMap(entry -> entry.payee().stream()).map(ids::get).collect(java.util.stream.Collectors.toSet());
                LocalDataServices.games().recordRound(matchId + "-" + number, roomCode, mode, number, scores, winners);
            }
            LocalDataServices.games().recordLedger(matchId, ledger, names);
            saveStatus.setText("积分与逐笔流水已保存到本机");
        } catch (RuntimeException error) {
            saveStatus.setText("本机保存失败，当前结算仍可查看：" + error.getMessage());
        }
    }

    @FXML private void returnHome() { LanSession.closeCurrent(); AppNavigator.home(); }
}

package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.*;
import javafx.application.Platform;
import com.campus.mahjong.model.session.DemoSession;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import com.campus.mahjong.model.repository.GameRecordRepository.PlayerRoundScore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Optional;

public final class SettlementController {
    @FXML private Label resultTitle;
    @FXML private Label resultSubtitle;
    @FXML private Label progressLabel;
    @FXML private VBox roundRows;
    @FXML private VBox roomRanking;
    @FXML private VBox friendRanking;
    @FXML private Button nextRoundButton;
    private LanSession session;
    private Settlement networkResult;
    private AutoCloseable gameSubscription;
    private boolean disposed;

    @FXML
    private void initialize() {
        if (LanSession.current().isPresent()) {
            initializeNetwork();
            return;
        }
        var result = DemoSession.lastResult();
        if (result == null) throw new IllegalStateException("缺少本局结算数据");
        persist(result);
        boolean localWin = result.winner().equals(DemoSession.nickname);
        resultTitle.setText(localWin ? "漂亮！本局获胜" : "本局结束，再接再厉");
        resultSubtitle.setText("第 " + result.round() + " 轮 · " + result.reason() + " · 胜者 " + result.winner());
        progressLabel.setText("房间 " + DemoSession.roomCode + " · 已完成 " + result.round() + "/" + DemoSession.totalRounds() + " 轮");
        renderRoundRows(result);
        renderRanking(roomRanking, DemoSession.roomLeaderboard(), true);
        renderRanking(friendRanking, DemoSession.friendLeaderboard(), false);
        nextRoundButton.setText(DemoSession.hasNextRound() ? "下一轮  ›" : "完成牌局");
    }

    @FXML private void nextRound() {
        if (session != null) {
            int total = session.currentRoom().orElseThrow().settings().orElseThrow().rounds();
            if (networkResult.round() >= total) { returnHome(); return; }
            nextRoundButton.setDisable(true);
            session.nextRound().whenComplete((id, error) -> Platform.runLater(() -> {
                if (disposed) return;
                if (error != null) {
                    resultSubtitle.setText("无法开始下一轮：" + error.getMessage());
                    nextRoundButton.setDisable(!session.owner());
                }
            }));
            return;
        }
        if (DemoSession.hasNextRound()) {
            DemoSession.nextRound();
            AppNavigator.game();
        } else {
            AppNavigator.home();
        }
    }

    @FXML private void returnHome() {
        dispose();
        if (session != null) LanSession.closeCurrent();
        AppNavigator.home();
    }

    private void initializeNetwork() {
        session = LanSession.current().orElseThrow();
        networkResult = session.currentSettlement().orElseThrow();
        RoomSnapshot room = session.currentRoom().orElseThrow();
        int total = room.settings().orElseThrow().rounds();
        String winner = networkResult.winner().flatMap(seat -> room.players().stream()
                .filter(player -> player.seat() == seat).findFirst())
                .map(player -> player.profile().nickname()).orElse("流局");
        var deltas = new LinkedHashMap<String, Long>();
        networkResult.changes().forEach(change -> deltas.put(playerName(change.playerId()), change.delta()));
        renderRoundRows(new DemoSession.RoundResult(networkResult.round(), winner, networkResult.reason(), deltas));
        var ranking = networkResult.changes().stream()
                .sorted(Comparator.comparingLong(ScoreChange::after).reversed())
                .map(change -> new DemoSession.ScoreRow(playerName(change.playerId()), change.after())).toList();
        renderRanking(roomRanking, ranking, true);
        // 联机只显示本房权威排名，避免把某台机器的本地榜当成全局榜。
        friendRanking.setVisible(false);
        friendRanking.setManaged(false);
        resultTitle.setText(networkResult.round() >= total ? "本场对局已完成" : "本局结算");
        resultSubtitle.setText(networkResult.reason() + " · " + winner);
        progressLabel.setText("房间 " + session.roomCode() + " · 已完成 " + networkResult.round() + "/" + total + " 轮");
        boolean finished = networkResult.round() >= total;
        nextRoundButton.setText(finished ? "完成牌局" : session.owner() ? "开始下一轮" : "等待房主开始下一轮");
        nextRoundButton.setDisable(!finished && !session.owner());
        gameSubscription = session.observeGame(snapshot -> Platform.runLater(() -> {
            if (disposed || snapshot.gameId().equals(networkResult.gameId())) return;
            dispose();
            AppNavigator.game();
        }));
    }

    private String playerName(PlayerId id) {
        return session.currentRoom().orElseThrow().players().stream()
                .filter(player -> player.profile().id().equals(id))
                .map(player -> player.profile().nickname()).findFirst().orElse(id.value());
    }

    private void dispose() {
        disposed = true;
        if (gameSubscription != null) {
            try { gameSubscription.close(); } catch (Exception ignored) {}
            gameSubscription = null;
        }
    }

    private void renderRoundRows(DemoSession.RoundResult result) {
        roundRows.getChildren().clear();
        result.changes().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .forEach(entry -> {
                    boolean winner = entry.getKey().equals(result.winner());
                    HBox row = new HBox(14);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add(winner ? "settlement-winner" : "settlement-row");
                    Label badge = new Label(winner ? "胜" : "·");
                    badge.getStyleClass().add(winner ? "winner-badge" : "rank-number");
                    Label name = new Label(entry.getKey() + (entry.getKey().equals(DemoSession.nickname) ? "（我）" : ""));
                    name.getStyleClass().add("settlement-name");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    Label delta = new Label(signed(entry.getValue()));
                    delta.getStyleClass().add(entry.getValue() >= 0 ? "score-positive" : "score-negative");
                    row.getChildren().addAll(badge, name, spacer, delta);
                    roundRows.getChildren().add(row);
                });
    }

    private void renderRanking(VBox target, java.util.List<DemoSession.ScoreRow> rows, boolean room) {
        target.getChildren().removeIf(node -> node.getStyleClass().contains("ranking-line"));
        for (int index = 0; index < Math.min(4, rows.size()); index++) {
            var data = rows.get(index);
            HBox line = new HBox(8);
            line.getStyleClass().add("ranking-line");
            Label rank = new Label(String.valueOf(index + 1));
            rank.getStyleClass().add(index == 0 ? "rank-first" : "rank-number");
            Label name = new Label(data.name() + (data.name().equals(DemoSession.nickname) ? "（我）" : ""));
            name.getStyleClass().add("rank-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label score = new Label(room ? signed(data.score()) : String.format("%,d", data.score()));
            score.getStyleClass().add("rank-score");
            line.getChildren().addAll(rank, name, spacer, score);
            target.getChildren().add(line);
        }
    }

    private String signed(long value) { return value > 0 ? "+" + value : String.valueOf(value); }

    private void persist(DemoSession.RoundResult result) {
        var scores = new LinkedHashMap<com.campus.mahjong.model.common.MahjongTypes.PlayerId, PlayerRoundScore>();
        result.changes().forEach((name, delta) -> scores.put(DemoSession.playerId(name), new PlayerRoundScore(name, delta)));
        Optional<com.campus.mahjong.model.common.MahjongTypes.PlayerId> winner = result.winner().equals("流局")
                ? Optional.empty() : Optional.of(DemoSession.playerId(result.winner()));
        LocalDataServices.games().recordRound(DemoSession.roomCode + "-" + result.round(),
                DemoSession.roomCode, DemoSession.mode, result.round(), scores, winner);
    }

    private enum MapEntryComparator implements Comparator<java.util.Map.Entry<String, Long>> {
        INSTANCE;
        @Override public int compare(java.util.Map.Entry<String, Long> left, java.util.Map.Entry<String, Long> right) {
            return Long.compare(right.getValue(), left.getValue());
        }
    }
}

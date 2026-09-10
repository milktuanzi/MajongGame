package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.ActionOption;
import com.campus.mahjong.model.common.MahjongTypes.GameSnapshot;
import com.campus.mahjong.model.common.MahjongTypes.GameStatus;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.common.MahjongTypes.PlayerPublicState;
import com.campus.mahjong.model.common.MahjongTypes.RoomPlayer;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.common.MahjongTypes.Tile;
import com.campus.mahjong.model.game.TileType;
import com.campus.mahjong.model.game.Meld;
import com.campus.mahjong.model.session.DemoSession;
import com.campus.mahjong.view.component.MahjongTileView;
import com.campus.mahjong.view.component.TableSeatLayout;
import com.campus.mahjong.view.component.PlayerInfoView;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.scene.Group;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.animation.Interpolator;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/** 同时支持本地演示和热点联机快照的牌桌控制器。 */
public final class GameController {
    @FXML private Label roundLabel;
    @FXML private Label roomLabel;
    @FXML private Label wallLabel;
    @FXML private PlayerInfoView playerLabel;
    @FXML private Label scoreLabel;
    @FXML private Label actionTip;
    @FXML private Label selectedTileLabel;
    @FXML private PlayerInfoView topPlayerLabel;
    @FXML private PlayerInfoView leftPlayerLabel;
    @FXML private PlayerInfoView rightPlayerLabel;
    @FXML private Pane playerOverlay;
    @FXML private VBox localDock;
    @FXML private HBox handPane;
    @FXML private StackPane tableViewport;
    @FXML private Pane tableBoard;
    @FXML private HBox bottomMeldPane;
    @FXML private HBox rightMeldPane;
    @FXML private HBox topMeldPane;
    @FXML private HBox leftMeldPane;
    @FXML private FlowPane bottomDiscardPane;
    @FXML private FlowPane rightDiscardPane;
    @FXML private FlowPane leftDiscardPane;
    @FXML private FlowPane topDiscardPane;
    @FXML private Button discardButton;
    @FXML private Button exchangeButton;
    private final List<String> exchangeSelection = new java.util.ArrayList<>();
    private final java.util.Map<String, Integer> exchangeRendered = new java.util.HashMap<>();
    @FXML private Button passButton;
    @FXML private Button missingWanButton;
    @FXML private Button missingPinButton;
    @FXML private Button missingSouButton;
    @FXML private Button chiButton;
    @FXML private Button pengButton;
    @FXML private Button gangButton;
    @FXML private Button huButton;
    @FXML private Label turnTimerLabel;
    @FXML private Label turnPhaseLabel;
    @FXML private Label bottomTurnIndicator;
    @FXML private Label rightTurnIndicator;
    @FXML private Label topTurnIndicator;
    @FXML private Label leftTurnIndicator;
    private final Timeline turnClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTurnClock()));
    @FXML private CheckBox reducedMotion;
    @FXML private Label actionFeedback;
    private javafx.animation.SequentialTransition feedbackAnimation;
    private java.util.Set<Seat> previousWinners = java.util.Set.of();
    private final java.util.Map<HBox, String> meldSignatures = new java.util.HashMap<>();
    private String lastDrawKey = "";
    private final java.util.Map<FlowPane, Integer> discardCounts = new java.util.HashMap<>();
    private boolean settlementShown;
    private String selectedTile;
    private boolean selectedIsDrawn;
    private MahjongTileView selectedView;
    private PauseTransition simulationPause;
    private LanSession lanSession;
    private GameSnapshot networkGame;
    private AutoCloseable gameSubscription;

    @FXML
    private void initialize() {
        tableViewport.widthProperty().addListener((obs, oldValue, value) -> fitTable());
        tableViewport.heightProperty().addListener((obs, oldValue, value) -> fitTable());
        Platform.runLater(this::fitTable);
        turnClock.setCycleCount(Timeline.INDEFINITE);
        tableViewport.sceneProperty().addListener((obs, previous, scene) -> {
            if (scene == null) turnClock.stop();
            else turnClock.play();
        });
        Optional<LanSession> current = LanSession.current();
        if (current.isPresent() && current.get().currentGame().isPresent()) {
            initializeNetworkGame(current.get());
            return;
        }
        roundLabel.setText("第 " + DemoSession.currentRound() + " / " + DemoSession.totalRounds() + " 轮");
        roomLabel.setText("房间 " + DemoSession.roomCode + " · 四川麻将（本地模拟）");
        renderPlayerPositions();
        scoreLabel.setText("本房积分 " + signed(DemoSession.roomScores().getOrDefault(DemoSession.nickname, 0L)));
        actionTip.setText("请在 15 秒内出牌；缺门牌排在最右侧，新摸牌以间隔区分");
        renderTable(true);
    }

    private void initializeNetworkGame(LanSession session) {
        lanSession = session;
        networkGame = session.currentGame().orElseThrow();
        if (networkGame.status() == GameStatus.FINISHED) { Platform.runLater(this::showFinalSettlement); return; }
        updateNetworkLabels();
        renderPlayerPositions();
        renderTable(true);
        gameSubscription = session.observeGame(snapshot -> runOnFx(() -> {
            boolean drewNewTile = networkGame == null || snapshot.revision() != networkGame.revision();
            networkGame = snapshot;
            if (drewNewTile || !snapshot.exchangingTiles()) exchangeSelection.clear();
            if (showFinalSettlement()) return;
            clearSelection();
            updateNetworkLabels();
            renderPlayerPositions();
            renderTable(drewNewTile);
        }));
    }

    private void updateNetworkLabels() {
        int totalRounds = lanSession.currentRoom().orElseThrow().settings().orElseThrow().rounds();
        roundLabel.setText("第 " + networkGame.currentRound() + " / " + totalRounds + " 轮");
        roomLabel.setText("好友房 · " + lanSession.invitation().substring(lanSession.invitation().lastIndexOf('#') + 1));
        long score = localPublicState().map(PlayerPublicState::score).orElse(0L);
        scoreLabel.setText("本房积分 " + signed(score));
        EnumSet<PlayerActionType> actions = currentActions();
        if (networkGame.status() == GameStatus.FINISHED) {
            actionTip.setText("所有轮次已结束");
        } else if (networkGame.winners().contains(localSeat())) {
            actionTip.setText("你已胡牌，正在观看其余玩家继续；本轮结束后自动进入下一轮");
        } else if (actions.contains(PlayerActionType.DISCARD)) {
            actionTip.setText("请在 15 秒内出牌；超时自动出牌，优先打缺门");
        } else if (actions.contains(PlayerActionType.PASS)) {
            actionTip.setText("请响应上一张弃牌");
        } else {
            actionTip.setText("等待其他玩家操作…");
        }
    }

    @FXML
    private void discardSelected() {
        if (selectedTile == null || selectedView == null) {
            actionTip.setText("请先选择一张手牌");
            return;
        }
        discardButton.setDisable(true);
        handPane.setMouseTransparent(true);
        TranslateTransition move = new TranslateTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 220), selectedView);
        move.setByY(reducedMotion.isSelected() ? 0 : -90);
        move.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fade = new FadeTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 220), selectedView);
        fade.setToValue(.1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 220), selectedView);
        scale.setToX(.78); scale.setToY(.78);
        String tile = selectedTile;
        boolean isDrawn = selectedIsDrawn;
        ParallelTransition transition = new ParallelTransition(move, fade, scale);
        transition.setOnFinished(event -> {
            if (lanSession != null) {
                clearSelection();
                handPane.setMouseTransparent(false);
                submitNetworkAction(PlayerActionType.DISCARD, List.of(new Tile(tile)));
                return;
            }
            actionTip.setText(DemoSession.discard(tile, isDrawn));
            clearSelection();
            handPane.setMouseTransparent(false);
            renderTable(true);
            if (DemoSession.roundFinished()) AppNavigator.settlement();
            else scheduleSimulationStep();
        });
        transition.play();
    }

    @FXML private void chooseMissingWan() { chooseMissingSuit(TileType.MAN_1); }
    @FXML private void chooseMissingPin() { chooseMissingSuit(TileType.PIN_1); }
    @FXML private void chooseMissingSou() { chooseMissingSuit(TileType.SOU_1); }
    private void chooseMissingSuit(TileType tile) {
        if (lanSession != null) {
            missingWanButton.setDisable(true); missingPinButton.setDisable(true); missingSouButton.setDisable(true);
            submitNetworkAction(PlayerActionType.DING_QUE, List.of(new Tile(tile.displayName())));
        } else {
            try { DemoSession.chooseMissingSuit(tile.suit()); }
            catch (RuntimeException error) { actionTip.setText(error.getMessage()); }
            renderTable(false);
        }
    }

    private boolean choosingMissingSuit() { return lanSession == null ? DemoSession.choosingMissingSuit() : networkGame.choosingMissingSuit(); }
    private boolean exchangingTiles() { return lanSession == null ? DemoSession.exchangingTiles() : networkGame.exchangingTiles(); }
    private java.util.Set<Seat> exchangeReady() { return lanSession == null ? DemoSession.exchangeReady() : networkGame.exchangeReady(); }
    private String exchangeSummary() {
        return lanSession == null ? DemoSession.exchangeSummary() : networkGame.exchangeDirection().map(d -> d.label() + "换牌 · 收到 " + networkGame.receivedExchangeTiles().stream().map(Tile::code).collect(java.util.stream.Collectors.joining("、")) + " · ").orElse("");
    }
    @FXML private void confirmExchange() {
        if (exchangeSelection.size() != 3 || !currentActions().contains(PlayerActionType.EXCHANGE_THREE)) return;
        exchangeButton.setDisable(true);
        if (lanSession != null) submitNetworkAction(PlayerActionType.EXCHANGE_THREE, exchangeSelection.stream().map(Tile::new).toList());
        else {
            try { DemoSession.exchangeTiles(List.copyOf(exchangeSelection)); exchangeSelection.clear(); renderTable(false); }
            catch (IllegalArgumentException | IllegalStateException error) { renderTable(false); actionTip.setText(error.getMessage()); }
        }
    }
    private void toggleExchange(MahjongTileView view, String tile) {
        boolean selected = view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected"));
        if (selected) exchangeSelection.remove(tile);
        else {
            if (exchangeSelection.size() == 3) { actionTip.setText("已经选好三张，取消一张后可重新选择"); return; }
            if (!exchangeSelection.isEmpty() && TileType.fromDisplayName(exchangeSelection.getFirst()).suit() != TileType.fromDisplayName(tile).suit()) {
                actionTip.setText("三张牌必须同花色；更换花色请先取消已选牌"); return;
            }
            exchangeSelection.add(tile);
        }
        view.setSelectedState(!selected); view.setTranslateY(selected ? 0 : -12);
        renderActionButtons();
    }
    private java.util.Map<Seat, TileType.Suit> missingSuits() { return lanSession == null ? DemoSession.missingSuits() : networkGame.missingSuits(); }
    private String suitName(TileType.Suit suit) { return switch(suit) { case MAN -> "万"; case PIN -> "筒"; case SOU -> "条"; default -> ""; }; }
    private boolean canDiscardTile(String name) {
        return lanSession == null ? DemoSession.discardableTiles().contains(name) : networkGame.availableActions().stream()
                .filter(action -> action.type() == PlayerActionType.DISCARD).flatMap(action -> action.relatedTiles().stream())
                .anyMatch(tile -> tile.code().equals(name));
    }

    @FXML private void passAction() { applyClaim(PlayerActionType.PASS); }
    @FXML private void chiAction() { applyClaim(PlayerActionType.CHI); }
    @FXML private void pengAction() { applyClaim(PlayerActionType.PENG); }
    @FXML
    private void gangAction() {
        if (lanSession != null) {
            boolean claim = currentActions().contains(PlayerActionType.PASS);
            if (!claim && selectedTile == null) {
                actionTip.setText("请选择一张可补杠或暗杠的手牌");
                return;
            }
            submitNetworkAction(PlayerActionType.GANG,
                    claim ? List.of() : List.of(new Tile(selectedTile)));
            return;
        }
        if (DemoSession.isAwaitingLocalClaim()) {
            applyClaim(PlayerActionType.GANG);
            return;
        }
        if (selectedTile == null) {
            actionTip.setText("请选择一张可补杠或暗杠的手牌");
            return;
        }
        actionTip.setText(DemoSession.declareLocalGang(selectedTile));
        clearSelection();
        renderTable(true);
        if (DemoSession.roundFinished()) AppNavigator.settlement();
        else scheduleSimulationStep();
    }

    @FXML
    private void winRound() {
        if (lanSession != null) {
            submitNetworkAction(PlayerActionType.HU, List.of());
            return;
        }
        try {
            DemoSession.declareLocalWin();
            clearSelection();
            renderTable(true);
            if (DemoSession.roundFinished()) AppNavigator.settlement();
            else scheduleSimulationStep();
        } catch (IllegalStateException error) {
            actionTip.setText(error.getMessage());
            renderActionButtons();
        }
    }

    @FXML
    private void returnHome() {
        closeGameSubscription();
        if (lanSession != null) LanSession.closeCurrent();
        AppNavigator.home();
    }

    private void applyClaim(PlayerActionType action) {
        if (lanSession != null) {
            submitNetworkAction(action, List.of());
            return;
        }
        actionTip.setText(DemoSession.submitLocalClaim(action));
        clearSelection();
        renderTable(true);
        if (DemoSession.roundFinished()) AppNavigator.settlement();
        else scheduleSimulationStep();
    }

    private void submitNetworkAction(PlayerActionType action, List<Tile> tiles) {
        handPane.setMouseTransparent(true);
        discardButton.setDisable(true);
        actionTip.setText("正在等待房主服务器确认…");
        lanSession.perform(action, tiles).whenComplete((result, error) -> runOnFx(() -> {
            handPane.setMouseTransparent(false);
            if (error != null) {
                clearSelection();
                renderTable(false);
                actionTip.setText(rootMessage(error));
                return;
            }
            actionTip.setText(result.message());
            if (!result.accepted()) { clearSelection(); renderTable(false); actionTip.setText(result.message()); }
        }));
    }

    private void renderTable(boolean animateDraw) {
        exchangeRendered.clear();
        if (lanSession == null) {
            DemoSession.synchronizeProgress();
            roundLabel.setText("第 " + DemoSession.currentRound() + " / " + DemoSession.totalRounds() + " 轮");
            scoreLabel.setText("本房积分 " + signed(DemoSession.roomScores().getOrDefault(DemoSession.nickname, 0L)));
        }
        renderPlayerPositions();
        if (lanSession == null && DemoSession.winners().contains(localSeat()))
            actionTip.setText("你已胡牌，正在观看其余玩家继续；本轮结束后自动进入下一轮");
        renderMelds();
        java.util.Set<Seat> winners = lanSession == null ? DemoSession.winners() : networkGame.winners();
        if (!previousWinners.containsAll(winners)) showActionFeedback("胡");
        previousWinners = java.util.Set.copyOf(winners);
        updateTurnClock();
        handPane.getChildren().clear();
        boolean canDiscard = currentActions().contains(PlayerActionType.DISCARD);
        List<String> organizedHand = lanSession == null
                ? DemoSession.organizedHand()
                : networkGame.ownHand().stream().map(Tile::code).toList();
        for (String tileName : organizedHand) {
            handPane.getChildren().add(createHandTile(tileName, false, canDiscard));
        }
        Optional<String> drawnTile = lanSession == null
                ? DemoSession.drawnTile()
                : networkGame.drawnTile().map(Tile::code);
        drawnTile.ifPresent(tileName -> {
            Region gap = new Region();
            gap.setMinWidth(22); gap.setPrefWidth(22);
            gap.setUserData(TileType.fromDisplayName(tileName).suit() == missingSuits().get(localSeat()));
            handPane.getChildren().add(gap);
            MahjongTileView drawn = createHandTile(tileName, true, canDiscard);
            handPane.getChildren().add(drawn);
            String key = (lanSession == null ? DemoSession.currentRound() : networkGame.currentRound()) + ":"
                    + (lanSession == null ? DemoSession.remainingTiles() : networkGame.wallRemaining()) + ":" + tileName;
            if (animateDraw && !key.equals(lastDrawKey) && !reducedMotion.isSelected()) animateDraw(drawn);
            lastDrawKey = key;
        });

        // 稳定排序也涵盖新摸的牌及其间隔，确保缺门始终在整副手牌最右侧。
        List<Node> sortedTiles = handPane.getChildren().stream()
                .sorted(java.util.Comparator.comparing(node -> Boolean.TRUE.equals(node.getUserData()))).toList();
        handPane.getChildren().setAll(sortedTiles);

        if (drawnTile.isEmpty()) lastDrawKey = "";
        Seat localSeat = localSeat();
        renderSeatDiscards(bottomDiscardPane, localSeat);
        renderSeatDiscards(rightDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT));
        renderSeatDiscards(topDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP));
        renderSeatDiscards(leftDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT));
        wallLabel.setText("牌墙剩余 " + (lanSession == null
                ? DemoSession.remainingTiles() : networkGame.wallRemaining()) + " 张");
        renderActionButtons();
        Platform.runLater(this::fitTable);
    }

    private void renderPlayerPositions() {
        Seat localSeat = localSeat();
        updatePlayerInfo(playerLabel, localSeat);
        updatePlayerInfo(rightPlayerLabel, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT));
        updatePlayerInfo(topPlayerLabel, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP));
        updatePlayerInfo(leftPlayerLabel, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT));
    }

    private void updatePlayerInfo(PlayerInfoView view, Seat seat) {
        long points = lanSession == null ? DemoSession.roomScores().getOrDefault(DemoSession.playerName(seat), 0L)
                : networkGame.players().stream().filter(p -> p.seat() == seat).mapToLong(PlayerPublicState::score).findFirst().orElse(0);
        int round = lanSession == null ? DemoSession.currentRound() : networkGame.currentRound();
        var ledger = lanSession == null ? DemoSession.ledger() : networkGame.ledger();
        String win = ledger.stream().filter(entry -> entry.round() == round && entry.payee().filter(seat::equals).isPresent())
                .map(com.campus.mahjong.model.game.ScoreEntry::reason).filter(reason -> reason.equals("自摸") || reason.equals("点炮"))
                .findFirst().orElse("");
        view.update(playerCaption(seat), missingCaption(seat), points, win);
    }

    private void renderSeatDiscards(FlowPane pane, Seat seat) {
        pane.getChildren().clear();
        List<String> discards = lanSession == null
                ? DemoSession.discardsForSeat(seat)
                : networkGame.players().stream().filter(player -> player.seat() == seat).findFirst()
                .map(player -> player.discards().stream().map(Tile::code).toList()).orElse(List.of());
        Integer previousCount = discardCounts.put(pane, discards.size());
        discards.forEach(tileName -> {
            MahjongTileView tile = new MahjongTileView(TileType.fromDisplayName(tileName), true);
            tile.setMouseTransparent(true);
            tile.getStyleClass().add("discarded-tile");
            tile.setScaleX(.94);
            tile.setScaleY(.94);
            pane.getChildren().add(tile);
            if (!reducedMotion.isSelected() && previousCount != null && discards.size() > previousCount
                    && pane.getChildren().size() == discards.size()) {
                ScaleTransition settle = new ScaleTransition(Duration.millis(170), tile);
                settle.setFromX(1.08); settle.setFromY(1.08); settle.setToX(.94); settle.setToY(.94);
                settle.setInterpolator(Interpolator.EASE_OUT); settle.play();
            }
        });
    }

    private void fitTable() {
        double width = tableViewport.getWidth(), height = tableViewport.getHeight();
        // 为左右玩家信息保留桌外空间，窄而高的窗口也不压进桌面。
        double scale = Math.min((width - 400) / 1000, (height - 8) / 1000);
        if (scale <= 0) return;
        tableBoard.setScaleX(scale);
        tableBoard.setScaleY(scale);
        double sideScale = Math.min(1, ((width - 1000 * scale) / 2 - 16) / 300);
        double rightX = width - 8 - 300 * sideScale;
        placeSideInfo(topPlayerLabel, rightX, Math.max(18, height * .12), sideScale);
        placeSideInfo(leftPlayerLabel, 8, height * .32, sideScale);
        placeSideInfo(rightPlayerLabel, rightX, height * .42, sideScale);
        placeSideInfo(playerLabel, 8, Math.max(height * .52, height - 240), sideScale);
        // 手牌只在窄窗口缩放，独立于正方形牌桌的缩放比例。
        double handScale = Math.min(1, (width - 72) / 1140);
        localDock.setScaleX(handScale); localDock.setScaleY(handScale);
        double tableTop = (height - 1000 * scale) / 2;
        double dockTop = localDock.getHeight() > 0 ? localDock.getBoundsInParent().getMinY() : height - 164;
        if (actionTip.getScene() != null && actionTip.getHeight() > 0)
            dockTop = tableViewport.sceneToLocal(actionTip.localToScene(actionTip.getLayoutBounds())).getMinY();
        bottomMeldPane.setLayoutY(Math.min(910, (dockTop - 14 - tableTop) / scale - 44));
    }

    private void placeSideInfo(PlayerInfoView view, double x, double y, double scale) {
        view.setScaleX(scale); view.setScaleY(scale);
        view.relocate(x - 150 * (1 - scale), y - 37 * (1 - scale));
    }

    private void renderMelds() {
        Seat local = localSeat();
        renderSeatMelds(bottomMeldPane, local);
        renderSeatMelds(rightMeldPane, TableSeatLayout.seatAt(local, TableSeatLayout.Position.RIGHT));
        renderSeatMelds(topMeldPane, TableSeatLayout.seatAt(local, TableSeatLayout.Position.TOP));
        renderSeatMelds(leftMeldPane, TableSeatLayout.seatAt(local, TableSeatLayout.Position.LEFT));
    }

    private void renderSeatMelds(HBox pane, Seat seat) {
        pane.getChildren().clear();
        if (lanSession != null) {
            networkGame.players().stream().filter(player -> player.seat() == seat).findFirst()
                    .ifPresent(player -> player.exposedGroups().forEach(group -> {
                        List<TileType> tiles = group.stream().map(tile -> TileType.fromDisplayName(tile.code())).toList();
                        String label = tiles.size() == 4 ? "杠" : tiles.stream().distinct().count() == 1 ? "碰" : "吃";
                        addMeldGroup(pane, label, tiles);
                    }));
        } else {
            for (Meld meld : DemoSession.meldsForSeat(seat)) {
                addMeldGroup(pane, switch (meld.type()) {
                    case GANG -> "杠";
                    case PENG -> "碰";
                    default -> "吃";
                }, meld.tiles());
            }
        }
        String signature = pane.getChildren().stream()
                .map(node -> ((Group) node).getChildren().getFirst().getAccessibleText())
                .collect(java.util.stream.Collectors.joining("|"));
        String previous = meldSignatures.put(pane, signature);
        if (previous != null && !signature.isEmpty() && !signature.equals(previous)) {
            String latest = ((Group) pane.getChildren().getLast()).getChildren().getFirst().getAccessibleText();
            showActionFeedback(latest.substring(0, 1));
            if (!reducedMotion.isSelected()) {
                FadeTransition reveal = new FadeTransition(Duration.millis(200), pane);
                reveal.setFromValue(.35); reveal.setToValue(1); reveal.play();
            }
        }
    }

    private void addMeldGroup(HBox pane, String label, List<TileType> tileTypes) {
        HBox tiles = new HBox(0);
        tiles.setAccessibleText(label + "：" + tileTypes.stream().map(TileType::displayName)
                .collect(java.util.stream.Collectors.joining("、")));
        for (TileType tileType : tileTypes) {
            MahjongTileView tile = new MahjongTileView(tileType, true);
            tile.getStyleClass().add("exposed-tile");
            tiles.getChildren().add(tile);
        }
        // Group 使用缩放后的边界参加父布局，四组杠牌也可保持单排。
        tiles.setScaleX(.94);
        tiles.setScaleY(.94);
        pane.getChildren().add(new Group(tiles));
    }

    private void showActionFeedback(String text) {
        if (feedbackAnimation != null) feedbackAnimation.stop();
        actionFeedback.setText(text); actionFeedback.setVisible(true);
        FadeTransition enter = new FadeTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 130), actionFeedback);
        enter.setFromValue(0); enter.setToValue(1);
        FadeTransition leave = new FadeTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 220), actionFeedback);
        leave.setFromValue(1); leave.setToValue(0);
        feedbackAnimation = new javafx.animation.SequentialTransition(enter, new PauseTransition(Duration.millis(650)), leave);
        feedbackAnimation.setOnFinished(event -> actionFeedback.setVisible(false));
        feedbackAnimation.play();
    }

    private void updateTurnClock() {
        if (lanSession == null) {
            long previousRevision = DemoSession.stateRevision();
            DemoSession.synchronizeProgress();
            if (previousRevision != DemoSession.stateRevision()) {
                exchangeSelection.clear();
                clearSelection(); handPane.setMouseTransparent(false); renderTable(false);
                if (DemoSession.roundFinished()) AppNavigator.settlement();
                else scheduleSimulationStep();
                return;
            }
        }
        boolean finished = lanSession == null ? DemoSession.roundFinished() : networkGame.status() == GameStatus.FINISHED;
        boolean waiting = lanSession == null ? DemoSession.waitingForClaims() : networkGame.waitingForClaims();
        long started = lanSession == null ? DemoSession.actionStartedAtMillis() : networkGame.actionStartedAtMillis();
        Seat current = lanSession == null ? DemoSession.currentTurnSeat() : networkGame.currentTurn();
        long remaining = Math.max(0, Math.min(15, (started + com.campus.mahjong.model.game.MahjongRound.DISCARD_TIMEOUT_MILLIS - System.currentTimeMillis() + 999) / 1000));
        turnTimerLabel.setText(finished || waiting ? "—" : String.format("%02d", remaining));
        turnPhaseLabel.setText(finished ? "本局结束" : waiting ? "等待响应" : "出牌倒计时");
        turnTimerLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("urgent"), !finished && !waiting && !choosingMissingSuit() && remaining <= 5);
        if (choosingMissingSuit()) {
            long deadline = lanSession == null ? DemoSession.missingSuitDeadline() : networkGame.missingSuitDeadline();
            turnTimerLabel.setText(String.format("%02d", Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000)));
            turnPhaseLabel.setText("定缺倒计时");
        }
        Label[] indicators = {bottomTurnIndicator, rightTurnIndicator, topTurnIndicator, leftTurnIndicator};
        if (exchangingTiles()) {
            long deadline = lanSession == null ? DemoSession.exchangeDeadline() : networkGame.exchangeDeadline();
            turnTimerLabel.setText(String.format("%02d", Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000)));
            turnPhaseLabel.setText("换三张倒计时");
            turnTimerLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("urgent"), deadline - System.currentTimeMillis() <= 5000);
        }
        TableSeatLayout.Position[] positions = TableSeatLayout.Position.values();
        for (int i = 0; i < indicators.length; i++) {
            boolean active = !exchangingTiles() && !choosingMissingSuit() && !finished && !waiting && TableSeatLayout.seatAt(localSeat(), positions[i]) == current;
            indicators[i].pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), active);
            indicators[i].setAccessibleText(active ? "当前出牌玩家方向" : "");
        }
    }

    private MahjongTileView createHandTile(String tileName, boolean drawn, boolean canDiscard) {
        MahjongTileView view = new MahjongTileView(TileType.fromDisplayName(tileName), false);
        view.setDrawn(drawn);
        view.setDisable(!canDiscard || !canDiscardTile(tileName));
        view.setUserData(TileType.fromDisplayName(tileName).suit() == missingSuits().get(localSeat()));
        if (TileType.fromDisplayName(tileName).suit() == missingSuits().get(localSeat())) view.getStyleClass().add("missing-suit-tile");
        view.setOnMouseClicked(event -> selectTile(view, tileName, drawn));
        if (exchangingTiles()) {
            view.setDisable(!currentActions().contains(PlayerActionType.EXCHANGE_THREE));
            int occurrence = exchangeRendered.merge(tileName, 1, Integer::sum);
            boolean selected = occurrence <= java.util.Collections.frequency(exchangeSelection, tileName);
            view.setSelectedState(selected); view.setTranslateY(selected ? -12 : 0);
            view.setOnMouseClicked(event -> toggleExchange(view, tileName));
        }
        return view;
    }

    private void renderActionButtons() {
        EnumSet<PlayerActionType> actions = currentActions();
        show(exchangeButton, exchangingTiles());
        exchangeButton.setText(exchangeReady().contains(localSeat()) ? "已确认换牌" : "确认换三张");
        exchangeButton.setDisable(!actions.contains(PlayerActionType.EXCHANGE_THREE) || exchangeSelection.size() != 3);
        if (exchangingTiles()) {
            selectedTileLabel.setText("已选 " + exchangeSelection.size() + " / 3 张");
            actionTip.setText(exchangeReady().contains(localSeat()) ? "已锁定选牌 · 等待四家同时交换（" + exchangeReady().size() + "/4）" : "换三张：点选 3 张同花色牌 · 超时自动选牌 · 换完再定缺");
        } else if (selectedTile == null) selectedTileLabel.setText(choosingMissingSuit() ? "换牌完成" : "尚未选牌");
        boolean choose = actions.contains(PlayerActionType.DING_QUE);
        Button[] missingButtons = {missingWanButton, missingPinButton, missingSouButton};
        TileType.Suit[] suits = {TileType.Suit.MAN, TileType.Suit.PIN, TileType.Suit.SOU};
        List<String> own = lanSession == null ? DemoSession.hand() : java.util.stream.Stream.concat(networkGame.ownHand().stream(), networkGame.drawnTile().stream()).map(Tile::code).toList();
        for (int i = 0; i < 3; i++) {
            TileType.Suit suit = suits[i];
            long count = own.stream().filter(tile -> TileType.fromDisplayName(tile).suit() == suit).count();
            missingButtons[i].setText("缺" + suitName(suit) + " · " + count + " 张");
            show(missingButtons[i], choose); missingButtons[i].setDisable(false);
        }
        if (choosingMissingSuit()) actionTip.setText(exchangeSummary() + (choose ? "请选择定缺，超时自动定缺" : "已定缺，等待其他玩家确认"));
        else if (actions.contains(PlayerActionType.DISCARD) && missingSuits().containsKey(localSeat()))
            actionTip.setText("本轮缺" + suitName(missingSuits().get(localSeat())) + " · 缺门已排在最右侧 · 15 秒内出牌，超时优先打缺门");
        boolean claim = lanSession == null
                ? DemoSession.isAwaitingLocalClaim() : actions.contains(PlayerActionType.PASS);
        show(passButton, claim);
        show(chiButton, claim && actions.contains(PlayerActionType.CHI));
        show(pengButton, claim && actions.contains(PlayerActionType.PENG));
        boolean selfGang = actions.contains(PlayerActionType.DISCARD) && actions.contains(PlayerActionType.GANG);
        show(gangButton, (claim || selfGang) && actions.contains(PlayerActionType.GANG));
        gangButton.setText(claim ? "杠" : "补 / 暗杠");
        boolean selfHu = actions.contains(PlayerActionType.DISCARD) && actions.contains(PlayerActionType.HU);
        show(huButton, (claim || selfHu) && actions.contains(PlayerActionType.HU));
        boolean mayDiscard = actions.contains(PlayerActionType.DISCARD);
        show(discardButton, mayDiscard);
        discardButton.setDisable(!mayDiscard || selectedTile == null);
        if (claim) actionTip.setText("对手弃牌可响应，请选择吃、碰、杠、胡或过");
    }

    private void selectTile(MahjongTileView view, String tile, boolean drawn) {
        if (!currentActions().contains(PlayerActionType.DISCARD) || !canDiscardTile(tile)) return;
        handPane.getChildren().stream().filter(MahjongTileView.class::isInstance)
                .map(MahjongTileView.class::cast).filter(item -> item != view)
                .forEach(item -> returnToBase(item));
        view.setSelectedState(true);
        selectedView = view;
        selectedTile = tile;
        selectedIsDrawn = drawn;
        selectedTileLabel.setText(drawn ? "已选：" + tile + "（本轮摸牌）" : "已选：" + tile);
        discardButton.setDisable(false);
        actionTip.setText("点击“打出选中牌”确认；确认后其余手牌自动整理");
        if (localGangTiles().contains(tile)) {
            actionTip.setText("该牌可补杠/暗杠；点击“补 / 暗杠”确认，或继续打出");
        }
        TranslateTransition lift = new TranslateTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 130), view);
        lift.setInterpolator(Interpolator.EASE_OUT); lift.setToY(-12); lift.play();
    }

    private void returnToBase(MahjongTileView tile) {
        tile.setSelectedState(false);
        TranslateTransition fall = new TranslateTransition(Duration.millis(reducedMotion.isSelected() ? 1 : 140), tile);
        fall.setToY(0);
        fall.play();
    }

    private void animateDraw(MahjongTileView tile) {
        tile.setOpacity(0); tile.setTranslateY(12);
        FadeTransition fade = new FadeTransition(Duration.millis(240), tile); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(240), tile); slide.setToY(0); slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, slide).play();
    }

    private void scheduleSimulationStep() {
        if (lanSession != null) return;
        if (!DemoSession.shouldAdvanceSimulation()) return;
        if (simulationPause != null) simulationPause.stop();
        simulationPause = new PauseTransition(Duration.seconds(2));
        simulationPause.setOnFinished(event -> {
            actionTip.setText(DemoSession.advanceSimulationStep());
            renderTable(true);
            if (DemoSession.roundFinished()) {
                AppNavigator.settlement();
            } else {
                scheduleSimulationStep();
            }
        });
        simulationPause.play();
    }

    private void clearSelection() {
        if (!exchangingTiles()) exchangeSelection.clear();
        selectedTile = null; selectedIsDrawn = false; selectedView = null;
        selectedTileLabel.setText("尚未选牌");
    }

    private void show(Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
    private String signed(long score) { return score > 0 ? "+" + score : String.valueOf(score); }
    private String playerCaption(Seat seat) {
        if (lanSession == null) return DemoSession.playerName(seat) + " · " + seatName(seat);
        String name = lanSession.currentRoom().orElseThrow().players().stream()
                .filter(player -> player.seat() == seat).map(RoomPlayer::profile)
                .map(profile -> profile.nickname() + (profile.id().equals(lanSession.localPlayer().id()) ? "（我）" : ""))
                .findFirst().orElse("等待玩家");
        return name + " · " + seatName(seat);
    }

    private String missingCaption(Seat seat) {
        if (exchangingTiles()) return exchangeReady().contains(seat) ? "已选三张" : "换牌中";
        if (choosingMissingSuit() && seat != localSeat()) {
            boolean ready = lanSession == null ? DemoSession.missingSuits().containsKey(seat) : networkGame.missingSuitReady().contains(seat);
            return ready ? "已定缺" : "定缺中";
        }
        TileType.Suit missing = missingSuits().get(seat);
        return missing == null ? "" : "缺" + suitName(missing);
    }

    private EnumSet<PlayerActionType> currentActions() {
        if (lanSession == null) return DemoSession.localActions();
        EnumSet<PlayerActionType> actions = EnumSet.noneOf(PlayerActionType.class);
        networkGame.availableActions().stream().map(ActionOption::type).forEach(actions::add);
        return actions;
    }

    private List<String> localGangTiles() {
        if (lanSession == null) return DemoSession.localGangTiles();
        return networkGame.availableActions().stream()
                .filter(option -> option.type() == PlayerActionType.GANG)
                .flatMap(option -> option.relatedTiles().stream()).map(Tile::code).toList();
    }

    private Optional<PlayerPublicState> localPublicState() {
        if (networkGame == null || lanSession == null) return Optional.empty();
        return networkGame.players().stream()
                .filter(player -> player.playerId().equals(lanSession.localPlayer().id())).findFirst();
    }

    private Seat localSeat() {
        return lanSession == null ? DemoSession.localSeat()
                : lanSession.currentRoom().orElseThrow().players().stream()
                .filter(player -> player.profile().id().equals(lanSession.localPlayer().id()))
                .map(RoomPlayer::seat).findFirst().orElseThrow();
    }

    private boolean showFinalSettlement() {
        if (networkGame == null || networkGame.status() != GameStatus.FINISHED) return false;
        if (!settlementShown) {
            settlementShown = true;
            turnClock.stop();
            closeGameSubscription();
            AppNavigator.settlement();
        }
        return true;
    }

    private void closeGameSubscription() {
        if (gameSubscription == null) return;
        try { gameSubscription.close(); } catch (Exception ignored) {}
        gameSubscription = null;
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run(); else Platform.runLater(action);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "网络操作失败" : current.getMessage();
    }
    private String seatName(Seat seat) {
        return switch (seat) {
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            case NORTH -> "北";
        };
    }
}

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
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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
    @FXML private Label playerLabel;
    @FXML private Label scoreLabel;
    @FXML private Label actionTip;
    @FXML private Label selectedTileLabel;
    @FXML private Label topPlayerLabel;
    @FXML private Label leftPlayerLabel;
    @FXML private Label rightPlayerLabel;
    @FXML private HBox handPane;
    @FXML private HBox meldPane;
    @FXML private FlowPane bottomDiscardPane;
    @FXML private FlowPane rightDiscardPane;
    @FXML private FlowPane leftDiscardPane;
    @FXML private FlowPane topDiscardPane;
    @FXML private Button discardButton;
    @FXML private Button passButton;
    @FXML private Button chiButton;
    @FXML private Button pengButton;
    @FXML private Button gangButton;
    @FXML private Button huButton;
    private String selectedTile;
    private boolean selectedIsDrawn;
    private MahjongTileView selectedView;
    private PauseTransition simulationPause;
    private LanSession lanSession;
    private GameSnapshot networkGame;
    private AutoCloseable gameSubscription;
    private AutoCloseable settlementSubscription;
    private boolean disposed;

    @FXML
    private void initialize() {
        Optional<LanSession> current = LanSession.current();
        if (current.isPresent() && current.get().currentGame().isPresent()) {
            initializeNetworkGame(current.get());
            return;
        }
        roundLabel.setText("第 " + DemoSession.currentRound() + " / " + DemoSession.totalRounds() + " 轮");
        roomLabel.setText("房间 " + DemoSession.roomCode + " · 四川麻将（本地模拟）");
        renderPlayerPositions();
        scoreLabel.setText("本房积分 " + signed(DemoSession.roomScores().getOrDefault(DemoSession.nickname, 0L)));
        actionTip.setText("轮到你出牌；本轮摸到的牌位于最右侧");
        renderTable(true);
    }

    private void initializeNetworkGame(LanSession session) {
        lanSession = session;
        networkGame = session.currentGame().orElseThrow();
        updateNetworkLabels();
        renderPlayerPositions();
        renderTable(true);
        gameSubscription = session.observeGame(snapshot -> runOnFx(() -> {
            if (disposed) return;
            boolean drewNewTile = networkGame == null || snapshot.revision() != networkGame.revision();
            networkGame = snapshot;
            clearSelection();
            updateNetworkLabels();
            renderPlayerPositions();
            renderTable(drewNewTile);
        }));
        settlementSubscription = session.observeSettlement(result -> Platform.runLater(() -> {
            if (disposed || !result.gameId().equals(networkGame.gameId())) return;
            closeGameSubscription();
            AppNavigator.settlement();
        }));
    }

    private void updateNetworkLabels() {
        int totalRounds = lanSession.currentRoom().orElseThrow().settings().orElseThrow().rounds();
        roundLabel.setText("第 " + networkGame.currentRound() + " / " + totalRounds + " 轮");
        roomLabel.setText("热点房间 " + lanSession.invitation() + " · 服务端权威同步");
        long score = localPublicState().map(PlayerPublicState::score).orElse(0L);
        scoreLabel.setText("本房积分 " + signed(score));
        EnumSet<PlayerActionType> actions = currentActions();
        if (networkGame.status() == GameStatus.SETTLING) {
            actionTip.setText("本局已经结束，正在接收结算…");
        } else if (actions.contains(PlayerActionType.DISCARD)) {
            actionTip.setText("轮到你出牌；操作将提交给房主服务器校验");
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
        TranslateTransition move = new TranslateTransition(Duration.millis(180), selectedView);
        move.setByY(-68);
        FadeTransition fade = new FadeTransition(Duration.millis(180), selectedView);
        fade.setToValue(.1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), selectedView);
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
    }

    @FXML
    private void winRound() {
        if (lanSession != null) {
            submitNetworkAction(PlayerActionType.HU, List.of());
            return;
        }
        try {
            DemoSession.declareLocalWin();
            AppNavigator.settlement();
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
            if (disposed) return;
            handPane.setMouseTransparent(false);
            if (error != null) {
                actionTip.setText(rootMessage(error));
                renderActionButtons();
                return;
            }
            actionTip.setText(result.message());
            if (!result.accepted()) renderActionButtons();
        }));
    }

    private void renderTable(boolean animateDraw) {
        renderMelds();
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
            handPane.getChildren().add(gap);
            MahjongTileView drawn = createHandTile(tileName, true, canDiscard);
            handPane.getChildren().add(drawn);
            if (animateDraw) animateDraw(drawn);
        });

        Seat localSeat = localSeat();
        renderSeatDiscards(bottomDiscardPane, localSeat);
        renderSeatDiscards(rightDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT));
        renderSeatDiscards(topDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP));
        renderSeatDiscards(leftDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT));
        wallLabel.setText("牌墙剩余 " + (lanSession == null
                ? DemoSession.remainingTiles() : networkGame.wallRemaining()) + " 张");
        renderActionButtons();
    }

    private void renderPlayerPositions() {
        Seat localSeat = localSeat();
        playerLabel.setText(playerCaption(localSeat));
        rightPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT)));
        topPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP)));
        leftPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT)));
    }

    private void renderSeatDiscards(FlowPane pane, Seat seat) {
        pane.getChildren().clear();
        List<String> discards = lanSession == null
                ? DemoSession.discardsForSeat(seat)
                : networkGame.players().stream().filter(player -> player.seat() == seat).findFirst()
                .map(player -> player.discards().stream().map(Tile::code).toList()).orElse(List.of());
        discards.forEach(tileName -> {
            MahjongTileView tile = new MahjongTileView(TileType.fromDisplayName(tileName), true);
            tile.setMouseTransparent(true);
            tile.getStyleClass().add("discarded-tile");
            tile.setScaleX(.78);
            tile.setScaleY(.78);
            pane.getChildren().add(tile);
        });
    }

    private void renderMelds() {
        meldPane.getChildren().clear();
        if (lanSession != null) {
            localPublicState().ifPresent(player -> player.exposedGroups().forEach(group ->
                    addMeldGroup(group.size() == 4 ? "杠" : group.size() == 3 ? "碰 / 吃" : "副露",
                            group.stream().map(tile -> TileType.fromDisplayName(tile.code())).toList())));
            meldPane.setVisible(!meldPane.getChildren().isEmpty());
            meldPane.setManaged(!meldPane.getChildren().isEmpty());
            return;
        }
        for (Meld meld : DemoSession.localMelds()) {
            addMeldGroup(meld.type() == PlayerActionType.GANG ? "杠" : "碰", meld.tiles());
        }
        meldPane.setVisible(!meldPane.getChildren().isEmpty());
        meldPane.setManaged(!meldPane.getChildren().isEmpty());
    }

    private void addMeldGroup(String label, List<TileType> tileTypes) {
        Label kind = new Label(label);
        kind.getStyleClass().add("meld-kind");
        HBox tiles = new HBox(2);
        tiles.setAlignment(javafx.geometry.Pos.CENTER);
        for (TileType tileType : tileTypes) {
            MahjongTileView tile = new MahjongTileView(tileType, false);
            tile.getStyleClass().add("exposed-tile");
            tile.setMouseTransparent(true);
            tiles.getChildren().add(tile);
        }
        VBox group = new VBox(2, kind, tiles);
        group.setAlignment(javafx.geometry.Pos.CENTER);
        group.getStyleClass().add("meld-group");
        meldPane.getChildren().add(group);
    }

    private MahjongTileView createHandTile(String tileName, boolean drawn, boolean canDiscard) {
        MahjongTileView view = new MahjongTileView(TileType.fromDisplayName(tileName), false);
        view.setDrawn(drawn);
        view.setDisable(!canDiscard);
        view.setOnMouseClicked(event -> selectTile(view, tileName, drawn));
        return view;
    }

    private void renderActionButtons() {
        EnumSet<PlayerActionType> actions = currentActions();
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
        if (!currentActions().contains(PlayerActionType.DISCARD)) return;
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
        TranslateTransition lift = new TranslateTransition(Duration.millis(110), view);
        lift.setToY(-9); lift.play();
    }

    private void returnToBase(MahjongTileView tile) {
        tile.setSelectedState(false);
        TranslateTransition fall = new TranslateTransition(Duration.millis(140), tile);
        fall.setToY(0);
        fall.play();
    }

    private void animateDraw(MahjongTileView tile) {
        tile.setOpacity(0); tile.setTranslateX(38);
        FadeTransition fade = new FadeTransition(Duration.millis(240), tile); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(290), tile); slide.setToX(0);
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

    private void closeGameSubscription() {
        disposed = true;
        if (settlementSubscription != null) {
            try { settlementSubscription.close(); } catch (Exception ignored) {}
            settlementSubscription = null;
        }
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

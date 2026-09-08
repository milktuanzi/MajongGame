package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
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

/** 四川麻将本地演示桌面的交互控制器。 */
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
    @FXML private Button pengButton;
    @FXML private Button gangButton;
    @FXML private Button huButton;
    private String selectedTile;
    private boolean selectedIsDrawn;
    private MahjongTileView selectedView;
    private PauseTransition simulationPause;

    @FXML
    private void initialize() {
        roundLabel.setText("第 " + DemoSession.currentRound() + " / " + DemoSession.totalRounds() + " 轮");
        roomLabel.setText("房间 " + DemoSession.roomCode + " · 四川麻将（本地模拟）");
        renderPlayerPositions();
        scoreLabel.setText("本房积分 " + signed(DemoSession.roomScores().getOrDefault(DemoSession.nickname, 0L)));
        actionTip.setText("轮到你出牌；本轮摸到的牌位于最右侧");
        renderTable(true);
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
    @FXML private void pengAction() { applyClaim(PlayerActionType.PENG); }
    @FXML
    private void gangAction() {
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
        try {
            DemoSession.declareLocalWin();
            AppNavigator.settlement();
        } catch (IllegalStateException error) {
            actionTip.setText(error.getMessage());
            renderActionButtons();
        }
    }

    @FXML private void returnHome() { AppNavigator.home(); }

    private void applyClaim(PlayerActionType action) {
        actionTip.setText(DemoSession.submitLocalClaim(action));
        clearSelection();
        renderTable(true);
        if (DemoSession.roundFinished()) AppNavigator.settlement();
        else scheduleSimulationStep();
    }

    private void renderTable(boolean animateDraw) {
        renderMelds();
        handPane.getChildren().clear();
        boolean canDiscard = DemoSession.localActions().contains(PlayerActionType.DISCARD);
        for (String tileName : DemoSession.organizedHand()) {
            handPane.getChildren().add(createHandTile(tileName, false, canDiscard));
        }
        DemoSession.drawnTile().ifPresent(tileName -> {
            Region gap = new Region();
            gap.setMinWidth(22); gap.setPrefWidth(22);
            handPane.getChildren().add(gap);
            MahjongTileView drawn = createHandTile(tileName, true, canDiscard);
            handPane.getChildren().add(drawn);
            if (animateDraw) animateDraw(drawn);
        });

        Seat localSeat = DemoSession.localSeat();
        renderSeatDiscards(bottomDiscardPane, localSeat);
        renderSeatDiscards(rightDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT));
        renderSeatDiscards(topDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP));
        renderSeatDiscards(leftDiscardPane, TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT));
        wallLabel.setText("牌墙剩余 " + DemoSession.remainingTiles() + " 张");
        renderActionButtons();
    }

    private void renderPlayerPositions() {
        Seat localSeat = DemoSession.localSeat();
        playerLabel.setText(playerCaption(localSeat));
        rightPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.RIGHT)));
        topPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.TOP)));
        leftPlayerLabel.setText(playerCaption(TableSeatLayout.seatAt(localSeat, TableSeatLayout.Position.LEFT)));
    }

    private void renderSeatDiscards(FlowPane pane, Seat seat) {
        pane.getChildren().clear();
        DemoSession.discardsForSeat(seat).forEach(tileName -> {
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
        for (Meld meld : DemoSession.localMelds()) {
            Label kind = new Label(meld.type() == PlayerActionType.GANG ? "杠" : "碰");
            kind.getStyleClass().add("meld-kind");
            HBox tiles = new HBox(2);
            tiles.setAlignment(javafx.geometry.Pos.CENTER);
            for (TileType tileType : meld.tiles()) {
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
        meldPane.setVisible(!meldPane.getChildren().isEmpty());
        meldPane.setManaged(!meldPane.getChildren().isEmpty());
    }

    private MahjongTileView createHandTile(String tileName, boolean drawn, boolean canDiscard) {
        MahjongTileView view = new MahjongTileView(TileType.fromDisplayName(tileName), false);
        view.setDrawn(drawn);
        view.setDisable(!canDiscard);
        view.setOnMouseClicked(event -> selectTile(view, tileName, drawn));
        return view;
    }

    private void renderActionButtons() {
        EnumSet<PlayerActionType> actions = DemoSession.localActions();
        boolean claim = DemoSession.isAwaitingLocalClaim();
        show(passButton, claim);
        show(pengButton, claim && actions.contains(PlayerActionType.PENG));
        boolean selfGang = actions.contains(PlayerActionType.DISCARD) && actions.contains(PlayerActionType.GANG);
        show(gangButton, (claim || selfGang) && actions.contains(PlayerActionType.GANG));
        gangButton.setText(claim ? "杠" : "补 / 暗杠");
        boolean selfHu = actions.contains(PlayerActionType.DISCARD) && actions.contains(PlayerActionType.HU);
        show(huButton, (claim || selfHu) && actions.contains(PlayerActionType.HU));
        boolean mayDiscard = actions.contains(PlayerActionType.DISCARD);
        show(discardButton, mayDiscard);
        discardButton.setDisable(!mayDiscard || selectedTile == null);
        if (claim) actionTip.setText("对手弃牌可响应，请选择碰、杠、胡或过");
    }

    private void selectTile(MahjongTileView view, String tile, boolean drawn) {
        if (!DemoSession.localActions().contains(PlayerActionType.DISCARD)) return;
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
        if (DemoSession.localGangTiles().contains(tile)) {
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
    private String playerCaption(Seat seat) { return DemoSession.playerName(seat) + " · " + seatName(seat); }
    private String seatName(Seat seat) {
        return switch (seat) {
            case EAST -> "东";
            case SOUTH -> "南";
            case WEST -> "西";
            case NORTH -> "北";
        };
    }
}

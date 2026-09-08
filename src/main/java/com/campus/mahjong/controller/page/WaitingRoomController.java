package com.campus.mahjong.controller.page;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.session.DemoSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Map;

public final class WaitingRoomController {
    @FXML private Label roomCodeLabel;
    @FXML private Label settingsLabel;
    @FXML private Label ownerBadge;
    @FXML private Label player1Name;
    @FXML private Label player2Name;
    @FXML private Label player3Name;
    @FXML private Label player4Name;
    @FXML private Label player1Status;
    @FXML private Label player2Status;
    @FXML private Label player3Status;
    @FXML private Label player4Status;
    @FXML private Label playerCountLabel;
    @FXML private ProgressBar roomProgress;
    @FXML private Button startButton;
    @FXML private Button readyButton;
    @FXML private Label roomStatusLabel;
    private boolean ready;

    @FXML
    private void initialize() {
        Map<ModeCode, String> names = Map.of(ModeCode.SICHUAN, "四川麻将", ModeCode.CHANGSHA, "长沙麻将",
                ModeCode.NORTHERN, "北方麻将", ModeCode.RED_CENTER, "红中麻将");
        roomCodeLabel.setText(DemoSession.roomCode);
        String cap = DemoSession.scoreCap.equals("不封顶") ? "不封顶" : DemoSession.scoreCap + "封顶";
        settingsLabel.setText(names.get(DemoSession.mode) + " · " + DemoSession.rounds + " · "
                + DemoSession.multiplier + " · " + cap);
        refreshPlayers();
        ownerBadge.setText(DemoSession.owner ? "房主" : "玩家");
        startButton.setVisible(DemoSession.owner);
        startButton.setManaged(DemoSession.owner);
        readyButton.setVisible(!DemoSession.owner);
        readyButton.setManaged(!DemoSession.owner);
        ready = DemoSession.owner;
        updateControls();
    }

    @FXML private void toggleReady() {
        ready = !ready;
        DemoSession.setLocalReady(ready);
        readyButton.setText(ready ? "已准备" : "准备");
        refreshPlayers();
        updateControls();
    }
    @FXML private void copyRoomCode() {
        ClipboardContent content = new ClipboardContent();
        content.putString(DemoSession.roomCode);
        Clipboard.getSystemClipboard().setContent(content);
        roomStatusLabel.setText("房间码已复制，可发送给好友");
    }
    @FXML private void startGame() {
        if (!DemoSession.allReady()) {
            roomStatusLabel.setText("仍有玩家未准备，暂时无法开始");
            return;
        }
        DemoSession.startGame();
        AppNavigator.game();
    }
    @FXML private void leaveRoom() { AppNavigator.home(); }

    private void refreshPlayers() {
        var players = DemoSession.players();
        Label[] names = {player1Name, player2Name, player3Name, player4Name};
        Label[] statuses = {player1Status, player2Status, player3Status, player4Status};
        for (int index = 0; index < names.length; index++) {
            if (index < players.size()) {
                var player = players.get(index);
                names[index].setText(player.name());
                statuses[index].setText(player.ready() ? "已准备" : "未准备");
                statuses[index].getStyleClass().setAll(player.ready() ? "ready-text" : "waiting-text");
            } else {
                names[index].setText("等待好友加入");
                statuses[index].setText("分享房间码");
            }
        }
        playerCountLabel.setText(players.size() + "/4 位玩家已连接");
        roomProgress.setProgress(players.size() / 4.0);
    }

    private void updateControls() {
        startButton.setDisable(!DemoSession.allReady());
        if (DemoSession.owner) {
            roomStatusLabel.setText(DemoSession.allReady() ? "全员准备完毕，可以开始游戏" : "等待所有玩家准备…");
        } else {
            roomStatusLabel.setText(ready ? "你已准备，等待房主开始" : "请准备后等待房主开始");
        }
    }
}

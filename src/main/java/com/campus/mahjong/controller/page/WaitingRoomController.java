package com.campus.mahjong.controller.page;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.session.DemoSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.Map;

public final class WaitingRoomController {
    @FXML private Label roomCodeLabel;
    @FXML private Label settingsLabel;
    @FXML private Label ownerBadge;
    @FXML private Label player1Name;
    @FXML private Button startButton;
    @FXML private Button readyButton;
    @FXML private Label roomStatusLabel;
    private boolean ready;

    @FXML
    private void initialize() {
        Map<ModeCode, String> names = Map.of(ModeCode.SICHUAN, "四川麻将", ModeCode.CHANGSHA, "长沙麻将",
                ModeCode.NORTHERN, "北方麻将", ModeCode.RED_CENTER, "红中麻将");
        roomCodeLabel.setText(DemoSession.roomCode);
        settingsLabel.setText(names.get(DemoSession.mode) + " · " + DemoSession.rounds + " · "
                + DemoSession.multiplier + " · " + DemoSession.scoreCap + "封顶");
        player1Name.setText(DemoSession.nickname);
        ownerBadge.setText(DemoSession.owner ? "房主" : "玩家");
        startButton.setVisible(DemoSession.owner);
        startButton.setManaged(DemoSession.owner);
    }

    @FXML private void toggleReady() {
        ready = !ready;
        readyButton.setText(ready ? "已准备" : "准备");
        if (!DemoSession.owner) roomStatusLabel.setText(ready ? "已准备，等待房主开始" : "请准备后等待房主开始");
    }
    @FXML private void copyRoomCode() { roomStatusLabel.setText("房间码已复制，可发送给好友"); }
    @FXML private void startGame() { AppNavigator.game(); }
    @FXML private void leaveRoom() { AppNavigator.home(); }
}

package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.session.DemoSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class GameController {
    @FXML private Label roundLabel;
    @FXML private Label playerLabel;
    @FXML private Label actionTip;

    @FXML private void initialize() {
        roundLabel.setText("第 1 / " + DemoSession.rounds.replace(" 轮", "") + " 轮");
        playerLabel.setText(DemoSession.nickname + " · 东家");
    }
    @FXML private void performAction() { actionTip.setText("操作已发送，等待其他玩家响应…"); }
    @FXML private void finishDemoRound() { AppNavigator.settlement(); }
    @FXML private void returnHome() { AppNavigator.home(); }
}

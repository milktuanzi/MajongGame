package com.campus.mahjong.controller.page;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.session.DemoSession;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import java.util.Map;

/** 联机好友房入口：创建房间或输入邀请码加入。 */
public final class HomeController {
    @FXML private TextField nicknameField;
    @FXML private TextField roomCodeField;
    @FXML private ComboBox<String> roundsBox;
    @FXML private ComboBox<String> multiplierBox;
    @FXML private ComboBox<String> scoreCapBox;
    @FXML private Label modeHintLabel;
    @FXML private Label errorLabel;
    @FXML private Label rank1Name;
    @FXML private Label rank1Score;
    @FXML private Label rank2Name;
    @FXML private Label rank2Score;
    @FXML private Label rank3Name;
    @FXML private Label rank3Score;
    @FXML private Button sichuan;
    @FXML private Button changsha;
    @FXML private Button northern;
    @FXML private Button redCenter;

    private final Map<String, ModeCode> modes = Map.of(
            "sichuan", ModeCode.SICHUAN, "changsha", ModeCode.CHANGSHA,
            "northern", ModeCode.NORTHERN, "redCenter", ModeCode.RED_CENTER);
    private final Map<ModeCode, String> names = Map.of(
            ModeCode.SICHUAN, "四川麻将", ModeCode.CHANGSHA, "长沙麻将",
            ModeCode.NORTHERN, "北方麻将", ModeCode.RED_CENTER, "红中麻将");
    private ModeCode selectedMode = ModeCode.SICHUAN;

    @FXML
    private void initialize() {
        nicknameField.setText(DemoSession.nickname);
        roundsBox.getItems().setAll("4 轮", "8 轮", "16 轮");
        multiplierBox.getItems().setAll("1 倍", "2 倍", "5 倍", "10 倍");
        scoreCapBox.getItems().setAll("不封顶", "64 分", "128 分", "256 分");
        roundsBox.setValue("8 轮");
        multiplierBox.setValue("2 倍");
        scoreCapBox.setValue("128 分");
        refreshLeaderboard();
    }

    @FXML
    private void chooseMode(ActionEvent event) {
        Button selected = (Button) event.getSource();
        selectedMode = modes.get(selected.getId());
        List.of(sichuan, changsha, northern, redCenter)
                .forEach(button -> button.getStyleClass().remove("selected"));
        selected.getStyleClass().add("selected");
        modeHintLabel.setText("已选择 " + names.get(selectedMode));
    }

    @FXML
    private void createRoom() {
        if (!validNickname()) return;
        savePlayer();
        DemoSession.createRoom(nicknameField.getText().trim(), selectedMode,
                roundsBox.getValue(), multiplierBox.getValue(), scoreCapBox.getValue());
        AppNavigator.waitingRoom();
    }

    @FXML
    private void joinRoom() {
        if (!validNickname()) return;
        String code = roomCodeField.getText() == null ? "" : roomCodeField.getText().trim();
        if (!code.matches("\\d{6}")) {
            errorLabel.setText("请输入房主提供的 6 位房间码");
            return;
        }
        savePlayer();
        DemoSession.joinRoom(nicknameField.getText().trim(), code);
        AppNavigator.waitingRoom();
    }

    private boolean validNickname() {
        if (nicknameField.getText() == null || nicknameField.getText().isBlank()) {
            errorLabel.setText("请先填写你的昵称");
            nicknameField.requestFocus();
            return false;
        }
        errorLabel.setText("");
        return true;
    }

    private void refreshLeaderboard() {
        var stored = LocalDataServices.games().leaderboard(3);
        Label[] names = {rank1Name, rank2Name, rank3Name};
        Label[] scores = {rank1Score, rank2Score, rank3Score};
        if (!stored.isEmpty()) {
            for (int index = 0; index < names.length; index++) {
                if (index < stored.size()) {
                    var row = stored.get(index);
                    names[index].setText(row.nickname() + (row.nickname().equals(DemoSession.nickname) ? "（我）" : ""));
                    scores[index].setText(String.format("%,d", row.totalScore()));
                } else { names[index].setText("等待新玩家"); scores[index].setText("0"); }
            }
            return;
        }
        var fallback = DemoSession.friendLeaderboard();
        for (int index = 0; index < names.length && index < fallback.size(); index++) {
            var row = fallback.get(index);
            names[index].setText(row.name() + (row.name().equals(DemoSession.nickname) ? "（我）" : ""));
            scores[index].setText(String.format("%,d", row.score()));
        }
    }

    private void savePlayer() {
        String name = nicknameField.getText().trim();
        LocalDataServices.players().save(new PlayerProfile(DemoSession.playerId(name), name, "", 0, 1));
    }
}

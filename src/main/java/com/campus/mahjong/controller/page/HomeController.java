package com.campus.mahjong.controller.page;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.model.session.DemoSession;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import com.campus.mahjong.infrastructure.network.client.LanInvitation;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import com.campus.mahjong.view.component.MahjongTileView;
import com.campus.mahjong.model.game.TileType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** 联机好友房入口：创建房间或输入邀请码加入。 */
public final class HomeController {
    @FXML private HBox heroTiles;
    @FXML private TextField nicknameField;
    @FXML private TextField roomCodeField;
    @FXML private ComboBox<String> roundsBox;
    @FXML private ComboBox<String> multiplierBox;
    @FXML private ComboBox<String> scoreCapBox;
    @FXML private Label modeHintLabel;
    @FXML private Label profileScoreLabel;
    @FXML private Label errorLabel;
    @FXML private Label rank1Name;
    @FXML private Label rank1Score;
    @FXML private Label rank2Name;
    @FXML private Label rank2Score;
    @FXML private Label rank3Name;
    @FXML private Label rank3Score;
    @FXML private Button sichuan;
    @FXML private Button redCenter;
    @FXML private Button createButton;
    @FXML private Button joinButton;

    private final Map<String, ModeCode> modes = Map.of(
            "sichuan", ModeCode.SICHUAN, "redCenter", ModeCode.RED_CENTER);
    private final Map<ModeCode, String> names = Map.of(
            ModeCode.SICHUAN, "四川麻将", ModeCode.RED_CENTER, "红中麻将");
    private ModeCode selectedMode = ModeCode.SICHUAN;

    @FXML
    private void initialize() {
        LocalDataServices.players().findMostRecent().ifPresentOrElse(profile -> {
            nicknameField.setText(profile.nickname());
            DemoSession.nickname = profile.nickname();
        }, () -> nicknameField.setText(DemoSession.nickname));
        refreshProfileScore();
        nicknameField.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) refreshProfileScore();
        });
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.visibleProperty().bind(errorLabel.managedProperty());
        int index = 0;
        for (TileType tile : List.of(TileType.MAN_1, TileType.GREEN, TileType.RED)) {
            MahjongTileView view = new MahjongTileView(tile, false);
            view.setRotate((index - 1) * 12);
            view.setTranslateY(index == 1 ? -12 : 4);
            heroTiles.getChildren().add(view);
            index++;
        }
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
        List.of(sichuan, redCenter)
                .forEach(button -> button.getStyleClass().remove("selected"));
        selected.getStyleClass().add("selected");
        modeHintLabel.setText(names.get(selectedMode));
    }

    @FXML
    private void createRoom() {
        if (!validNickname()) return;
        PlayerProfile player = savePlayer();
        FriendRoomSettings settings = new FriendRoomSettings(selectedMode,
                number(multiplierBox.getValue()), scoreCap(), number(roundsBox.getValue()), false, "");
        connect(LanSession.host(player, settings), "正在启动房主服务器…");
    }

    @FXML
    private void joinRoom() {
        if (!validNickname()) return;
        String invitation = roomCodeField.getText() == null ? "" : roomCodeField.getText().trim();
        try {
            LanInvitation.parse(invitation);
        } catch (IllegalArgumentException exception) {
            errorLabel.setText(exception.getMessage());
            return;
        }
        connect(LanSession.join(savePlayer(), invitation), "正在连接房主电脑…");
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
        for (int index = 0; index < names.length; index++) {
            names[index].setText(index == 0 ? "打完第一场，留下你的战绩" : "等待牌友入榜");
            scores[index].setText("—");
        }
    }

    private void refreshProfileScore() {
        String nickname = nicknameField.getText() == null ? "" : nicknameField.getText().trim();
        long score = LocalDataServices.players().findByNickname(nickname)
                .flatMap(profile -> LocalDataServices.games().findScore(profile.id()))
                .map(com.campus.mahjong.model.common.MahjongTypes.FriendScoreEntry::totalScore)
                .orElse(0L);
        profileScoreLabel.setText("本机积分  " + (score > 0 ? "+" : "") + score);
    }

    @FXML private void showHelp() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.setTitle("联机指南");
        help.setHeaderText("四位好友，一起入座");
        help.setContentText("1. 四台电脑连接同一 Wi-Fi 或热点。\n2. 房主选择玩法、轮次和倍率，创建好友房。\n3. 房主复制完整邀请地址，好友粘贴后加入。\n4. 全员准备后，由房主开始游戏。\n\n邀请格式：192.168.137.1:19090#836204\n首次联机请允许 Java 通过防火墙。\n\n胡牌后观战，剩余玩家继续；三家胡牌或牌墙耗尽自动换轮，全部轮次结束后展示总分与流水。");
        help.initOwner(nicknameField.getScene().getWindow());
        help.showAndWait();
    }

    @FXML private void showRanking() {
        var rows = LocalDataServices.games().leaderboard(100);
        Alert ranking = new Alert(Alert.AlertType.INFORMATION);
        ranking.setTitle("牌友积分榜"); ranking.setHeaderText("本机保存的累计战绩");
        ListView<String> list = new ListView<>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            list.getItems().add(String.format("%02d   %s    %,d 分 · %d 局 · %d 胜", i + 1, row.nickname(), row.totalScore(), row.games(), row.wins()));
        }
        list.setPlaceholder(new Label("还没有战绩，完成第一场好友局后再来看看。"));
        list.setPrefSize(490, 330); ranking.getDialogPane().setContent(list);
        ranking.initOwner(nicknameField.getScene().getWindow()); ranking.showAndWait();
    }

    private PlayerProfile savePlayer() {
        String name = nicknameField.getText().trim();
        PlayerId id = LocalDataServices.players().findByNickname(name)
                .map(PlayerProfile::id).orElseGet(() -> new PlayerId(UUID.randomUUID().toString()));
        PlayerProfile profile = new PlayerProfile(id, name, "", 0, 1);
        LocalDataServices.players().save(profile);
        DemoSession.nickname = name;
        return profile;
    }

    private void connect(CompletionStage<LanSession> connection, String progress) {
        setBusy(true);
        errorLabel.setText(progress);
        connection.whenComplete((session, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                errorLabel.setText(rootMessage(error));
                return;
            }
            LanSession.install(session);
            errorLabel.setText("");
            AppNavigator.waitingRoom();
        }));
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        joinButton.setDisable(busy);
    }

    private int number(String text) { return Integer.parseInt(text.replaceAll("\\D", "")); }

    private Optional<Integer> scoreCap() {
        return "不封顶".equals(scoreCapBox.getValue())
                ? Optional.empty() : Optional.of(number(scoreCapBox.getValue()));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "连接失败，请检查热点和防火墙" : current.getMessage();
    }
}

package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.*;
import com.campus.mahjong.model.session.DemoSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.Comparator;
import java.util.Map;

public final class WaitingRoomController {
    @FXML private Label roomCodeLabel;
    @FXML private Label invitationLabel;
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

    private final Map<ModeCode, String> modeNames = Map.of(
            ModeCode.SICHUAN, "四川麻将", ModeCode.CHANGSHA, "长沙麻将",
            ModeCode.NORTHERN, "北方麻将", ModeCode.RED_CENTER, "红中麻将");
    private LanSession lanSession;
    private AutoCloseable roomSubscription;
    private AutoCloseable gameSubscription;
    private boolean ready;
    private boolean gameOpened;
    private boolean navigationFailed;
    private boolean leaving;

    @FXML
    private void initialize() {
        LanSession.current().ifPresentOrElse(this::initializeLan, this::initializeDemo);
    }

    private void initializeLan(LanSession session) {
        lanSession = session;
        ownerBadge.setText(session.owner() ? "房主 · 临时服务器" : "热点玩家");
        startButton.setVisible(session.owner());
        startButton.setManaged(session.owner());
        readyButton.setVisible(!session.owner());
        readyButton.setManaged(!session.owner());
        roomSubscription = session.observe(session.currentRoom().orElseThrow().roomId(),
                snapshot -> runOnFx(() -> renderRoom(snapshot)));
        gameSubscription = session.observeGame(snapshot -> Platform.runLater(() -> {
            if (!navigationFailed) openGame();
        }));
    }

    private void openGame() {
        if (gameOpened || leaving) return;
        gameOpened = true;
        try {
            AppNavigator.game();
            closeSubscriptions();
        } catch (RuntimeException error) {
            gameOpened = false;
            navigationFailed = true;
            error.printStackTrace();
            startButton.setText("重新进入牌局 ›");
            readyButton.setText("重新进入牌局 ›");
            startButton.setDisable(false);
            readyButton.setDisable(false);
            roomStatusLabel.setText("牌桌加载失败，请点击重新进入：" + rootMessage(error));
        }
    }

    private void initializeDemo() {
        roomCodeLabel.setText(DemoSession.roomCode);
        invitationLabel.setText("");
        String cap = DemoSession.scoreCap.equals("不封顶") ? "不封顶" : DemoSession.scoreCap + "封顶";
        settingsLabel.setText(modeNames.get(DemoSession.mode) + " · " + DemoSession.rounds + " · "
                + DemoSession.multiplier + " · " + cap);
        refreshDemoPlayers();
        ownerBadge.setText(DemoSession.owner ? "房主" : "玩家");
        startButton.setVisible(DemoSession.owner);
        startButton.setManaged(DemoSession.owner);
        readyButton.setVisible(!DemoSession.owner);
        readyButton.setManaged(!DemoSession.owner);
        ready = DemoSession.owner;
        updateDemoControls();
    }

    @FXML
    private void toggleReady() {
        if (lanSession != null && lanSession.currentGame().isPresent()) { openGame(); return; }
        if (lanSession == null) {
            ready = !ready;
            DemoSession.setLocalReady(ready);
            readyButton.setText(ready ? "已准备" : "准备");
            refreshDemoPlayers();
            updateDemoControls();
            return;
        }
        boolean next = !ready;
        readyButton.setDisable(true);
        lanSession.setReady(lanSession.currentRoom().orElseThrow().roomId(),
                        lanSession.localPlayer().id(), next)
                .whenComplete((snapshot, error) -> runOnFx(() -> {
                    readyButton.setDisable(false);
                    if (error != null) roomStatusLabel.setText(rootMessage(error));
                }));
    }

    @FXML
    private void copyRoomCode() {
        ClipboardContent content = new ClipboardContent();
        content.putString(lanSession == null ? DemoSession.roomCode : lanSession.invitation());
        Clipboard.getSystemClipboard().setContent(content);
        roomStatusLabel.setText(lanSession == null
                ? "房间码已复制，可发送给好友"
                : "完整邀请地址已复制，请发给连接同一热点的好友");
    }

    @FXML
    private void startGame() {
        if (lanSession == null) {
            if (!DemoSession.allReady()) {
                roomStatusLabel.setText("仍有玩家未准备，暂时无法开始");
                return;
            }
            DemoSession.startGame();
            AppNavigator.game();
            return;
        }
        RoomSnapshot room = lanSession.currentRoom().orElseThrow();
        if (lanSession.currentGame().isPresent()) { openGame(); return; }
        if (room.status() == RoomStatus.PLAYING) {
            roomStatusLabel.setText("牌局已创建，正在等待牌桌数据…");
            return;
        }
        if (!allReady(room)) {
            roomStatusLabel.setText("必须四位玩家全部在线并准备");
            return;
        }
        startButton.setDisable(true);
        roomStatusLabel.setText("正在由房主服务器创建牌局…");
        lanSession.start(room.roomId(), lanSession.localPlayer().id())
                .whenComplete((gameId, error) -> runOnFx(() -> {
                    if (gameOpened || leaving) return;
                    if (error != null) {
                        startButton.setDisable(false);
                        roomStatusLabel.setText(rootMessage(error));
                    }
                }));
    }

    @FXML
    private void leaveRoom() {
        leaving = true;
        closeSubscriptions();
        if (lanSession == null) {
            AppNavigator.home();
            return;
        }
        RoomSnapshot room = lanSession.currentRoom().orElse(null);
        if (room == null) {
            LanSession.closeCurrent();
            AppNavigator.home();
            return;
        }
        lanSession.leave(room.roomId(), lanSession.localPlayer().id()).whenComplete((ignored, error) ->
                runOnFx(() -> {
                    LanSession.closeCurrent();
                    AppNavigator.home();
                }));
    }

    private void renderRoom(RoomSnapshot room) {
        roomCodeLabel.setText(lanSession.roomCode());
        invitationLabel.setText(lanSession.invitation());
        FriendRoomSettings settings = room.settings().orElseThrow();
        String cap = settings.scoreCap().map(value -> value + " 分封顶").orElse("不封顶");
        settingsLabel.setText(modeNames.get(settings.mode()) + " · " + settings.rounds() + " 轮 · "
                + settings.baseMultiplier() + " 倍 · " + cap);

        var players = room.players().stream().sorted(Comparator.comparing(RoomPlayer::seat)).toList();
        Label[] names = {player1Name, player2Name, player3Name, player4Name};
        Label[] statuses = {player1Status, player2Status, player3Status, player4Status};
        for (int index = 0; index < names.length; index++) {
            if (index < players.size()) {
                RoomPlayer player = players.get(index);
                boolean me = player.profile().id().equals(lanSession.localPlayer().id());
                names[index].setText(player.profile().nickname() + (me ? "（我）" : ""));
                String status = !player.connected() ? "已断线" : player.ready() ? "已准备" : "未准备";
                statuses[index].setText(status);
                statuses[index].getStyleClass().setAll(player.connected() && player.ready()
                        ? "ready-text" : "waiting-text");
                if (me) ready = player.ready();
            } else {
                names[index].setText("等待好友加入");
                statuses[index].setText("分享邀请地址");
                statuses[index].getStyleClass().setAll("waiting-text");
            }
        }
        long connected = players.stream().filter(RoomPlayer::connected).count();
        playerCountLabel.setText(connected + "/4 位玩家已连接 · revision " + room.revision());
        roomProgress.setProgress(connected / 4.0);
        readyButton.setText(ready ? "已准备" : "准备");
        startButton.setDisable(!allReady(room));
        if (room.status() == RoomStatus.PLAYING) {
            boolean received = lanSession.currentGame().isPresent();
            startButton.setText("进入牌局 ›");
            readyButton.setText("进入牌局 ›");
            startButton.setDisable(!received);
            readyButton.setDisable(!received);
            roomStatusLabel.setText(received ? "牌局已开始，点击进入牌桌" : "牌局已创建，正在等待牌桌数据…");
            return;
        }
        if (room.status() == RoomStatus.CLOSED) {
            roomStatusLabel.setText("房主已关闭房间");
        } else if (lanSession.owner()) {
            roomStatusLabel.setText(allReady(room) ? "全员准备完毕，可以开始游戏" : "等待好友通过邀请地址加入并准备…");
        } else {
            roomStatusLabel.setText(ready ? "你已准备，等待房主开始" : "请准备后等待房主开始");
        }
    }

    private boolean allReady(RoomSnapshot room) {
        return room.players().size() == room.requiredPlayers()
                && room.players().stream().allMatch(player -> player.connected() && player.ready());
    }

    private void refreshDemoPlayers() {
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

    private void updateDemoControls() {
        startButton.setDisable(!DemoSession.allReady());
        if (DemoSession.owner) {
            roomStatusLabel.setText(DemoSession.allReady() ? "全员准备完毕，可以开始游戏" : "等待所有玩家准备…");
        } else {
            roomStatusLabel.setText(ready ? "你已准备，等待房主开始" : "请准备后等待房主开始");
        }
    }

    private void closeSubscriptions() {
        close(roomSubscription);
        close(gameSubscription);
        roomSubscription = null;
        gameSubscription = null;
    }

    private void close(AutoCloseable subscription) {
        if (subscription == null) return;
        try { subscription.close(); } catch (Exception ignored) {}
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) action.run(); else Platform.runLater(action);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "网络操作失败" : current.getMessage();
    }
}

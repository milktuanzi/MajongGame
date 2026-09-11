package com.campus.mahjong.integration;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.common.MahjongTypes.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** 在独立 JVM 中加载真正的等待区、点击开局并检查牌桌，避免污染其他测试的 JavaFX 状态。 */
public class ModularGameStartupProbe {
    public static void main(String[] args) {
        var sessions = new ArrayList<LanSession>();
        var failure = new AtomicReference<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); failure.set(error); });
        int exit = 1;
        try {
            com.campus.mahjong.infrastructure.persistence.LocalDataServices.initialize(
                    java.nio.file.Files.createTempDirectory("mahjong-startup-db-").resolve("test.db"));
            if (!LanSession.class.getModule().isNamed()) throw new AssertionError("必须以模块模式运行");
            var settings = new FriendRoomSettings(ModeCode.SICHUAN, 2, Optional.of(128), 2, false, "");
            var host = LanSession.host(player("房主"), settings, 0, "127.0.0.1").toCompletableFuture().get(5, TimeUnit.SECONDS);
            sessions.add(host);
            for (int i = 1; i < 4; i++) {
                var guest = LanSession.join(player("玩家" + i), host.invitation()).toCompletableFuture().get(5, TimeUnit.SECONDS);
                sessions.add(guest);
                guest.setReady(guest.currentRoom().orElseThrow().roomId(), guest.localPlayer().id(), true).toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (host.currentRoom().orElseThrow().players().size() != 4 ||
                    host.currentRoom().orElseThrow().players().stream().anyMatch(p -> !p.ready())) {
                if (System.nanoTime() > readyDeadline) throw new AssertionError("准备同步超时");
                Thread.sleep(20);
            }
            LanSession.install(host);
            var stage = new AtomicReference<Stage>();
            var started = new CompletableFuture<Void>();
            Platform.startup(() -> {
                try {
                    var window = new Stage(); stage.set(window); AppNavigator.start(window);
                    AppNavigator.waitingRoom();
                    var start = (Button) window.getScene().lookup("#startButton");
                    if (start.isDisabled()) throw new AssertionError("四家准备后开局按钮仍禁用");
                    start.fire(); started.complete(null);
                } catch (Throwable error) { started.completeExceptionally(error); }
            });
            started.get(5, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            boolean entered = false;
            while (System.nanoTime() < deadline && failure.get() == null) {
                var check = new CompletableFuture<Boolean>();
                Platform.runLater(() -> check.complete(stage.get().getScene().lookup("#handPane") != null));
                if (check.get(2, TimeUnit.SECONDS)) { entered = true; break; }
                Thread.sleep(30);
            }
            if (!entered) throw new AssertionError("房间已开局但仍未进入牌桌", failure.get());
            if (sessions.stream().anyMatch(s -> s.currentGame().isEmpty())) throw new AssertionError("并非四端都收到牌局");
            System.out.println("PASS: production modules, four clients, ready → start button → game table");
            exit = 0;
        } catch (Throwable error) { error.printStackTrace(); }
        finally { sessions.reversed().forEach(LanSession::close); Platform.exit(); }
        System.exit(exit);
    }
    private static PlayerProfile player(String name) {
        return new PlayerProfile(new PlayerId(UUID.randomUUID().toString()), name, "", 0, 1);
    }
}

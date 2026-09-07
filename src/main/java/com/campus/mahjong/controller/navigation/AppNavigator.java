package com.campus.mahjong.controller.navigation;

import com.campus.mahjong.app.MainApplication;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/** 页面 Demo 的轻量导航器；正式版本可替换为 NavigationService 实现。 */
public final class AppNavigator {
    private static Stage stage;
    private static Scene scene;

    private AppNavigator() {}

    public static void start(Stage primaryStage) throws IOException {
        stage = primaryStage;
        Parent root = load("home-view.fxml");
        scene = new Scene(root, 1280, 760);
        scene.getStylesheets().add(MainApplication.class
                .getResource("/com/campus/mahjong/view/css/home.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    public static void home() { show("home-view.fxml"); }
    public static void waitingRoom() { show("waiting-room-view.fxml"); }
    public static void game() { show("game-view.fxml"); }
    public static void settlement() { show("settlement-view.fxml"); }

    private static void show(String fxml) {
        try {
            scene.setRoot(load(fxml));
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载页面: " + fxml, exception);
        }
    }

    private static Parent load(String fxml) throws IOException {
        return new FXMLLoader(MainApplication.class
                .getResource("/com/campus/mahjong/view/fxml/" + fxml)).load();
    }
}

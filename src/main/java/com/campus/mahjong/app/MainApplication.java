package com.campus.mahjong.app;

import com.campus.mahjong.controller.navigation.AppNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public final class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("雀隐 · 联机好友麻将");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        AppNavigator.start(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

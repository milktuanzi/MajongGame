package com.campus.mahjong.app;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

public final class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        LocalDataServices.initialize(Path.of("data", "mahjong.db"));
        stage.setTitle("闹麻麻 · 联机好友麻将");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        AppNavigator.start(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

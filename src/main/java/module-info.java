module com.campus.mahjong.contracts {
    requires javafx.controls;
    requires javafx.fxml;

    exports com.campus.mahjong.app;
    exports com.campus.mahjong.model.common;
    exports com.campus.mahjong.model.event;
    exports com.campus.mahjong.model.rule;
    exports com.campus.mahjong.model.service.game;
    exports com.campus.mahjong.model.service.mode;
    exports com.campus.mahjong.model.service.player;
    exports com.campus.mahjong.model.service.room;
    exports com.campus.mahjong.controller.navigation;

    opens com.campus.mahjong.controller.page to javafx.fxml;
}

module com.campus.mahjong.contracts {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    exports com.campus.mahjong.app;
    exports com.campus.mahjong.model.common;
    exports com.campus.mahjong.model.event;
    exports com.campus.mahjong.model.game;
    exports com.campus.mahjong.model.rule;
    exports com.campus.mahjong.model.rule.region;
    exports com.campus.mahjong.model.service.game;
    exports com.campus.mahjong.model.service.mode;
    exports com.campus.mahjong.model.service.player;
    exports com.campus.mahjong.model.service.room;
    exports com.campus.mahjong.controller.navigation;
    exports com.campus.mahjong.infrastructure.game;
    exports com.campus.mahjong.infrastructure.persistence;
    exports com.campus.mahjong.infrastructure.persistence.repository;
    exports com.campus.mahjong.infrastructure.persistence.service;

    opens com.campus.mahjong.controller.page to javafx.fxml;
}

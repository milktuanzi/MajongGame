package com.campus.mahjong.controller.navigation;

import com.campus.mahjong.model.common.MahjongTypes.PageId;
import javafx.scene.Parent;
import java.util.Map;

/** 将页面标识解析为 JavaFX 视图；实现可使用 FXML 或纯 JavaFX。 */
public interface ViewFactory {
    Parent create(PageId page, Map<String, Object> parameters);
}

package com.campus.mahjong.controller.navigation;

import com.campus.mahjong.model.common.MahjongTypes.PageId;
import java.util.Map;

/** JavaFX 页面之间只通过该接口跳转，控制器不直接持有 Stage。 */
public interface NavigationService {
    void navigateTo(PageId page, Map<String, Object> parameters);
    void back();
    PageId currentPage();
}

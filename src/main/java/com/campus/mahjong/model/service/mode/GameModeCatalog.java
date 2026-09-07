package com.campus.mahjong.model.service.mode;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.ModeInfo;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** 好友房创建页可选择的麻将规则目录。 */
public interface GameModeCatalog {
    CompletionStage<List<ModeInfo>> listModes();
    CompletionStage<ModeInfo> getMode(ModeCode mode);
}

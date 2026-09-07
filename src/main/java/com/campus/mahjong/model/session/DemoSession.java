package com.campus.mahjong.model.session;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;

/** 仅供页面 Demo 共用的数据；实际项目由联网会话服务替代。 */
public final class DemoSession {
    public static boolean owner = true;
    public static String roomCode = "836204";
    public static String nickname = "清风客";
    public static ModeCode mode = ModeCode.SICHUAN;
    public static String rounds = "8 轮";
    public static String multiplier = "2 倍";
    public static String scoreCap = "128 分";

    private DemoSession() {}
}

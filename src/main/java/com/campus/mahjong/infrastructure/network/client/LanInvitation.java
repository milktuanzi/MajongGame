package com.campus.mahjong.infrastructure.network.client;

/** 可复制给同一热点内好友的地址：host:port#6位房间码。 */
public record LanInvitation(String host, int port, String roomCode) {
    public LanInvitation {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("缺少房主地址");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("端口范围错误");
        if (roomCode == null || !roomCode.matches("\\d{6}")) throw new IllegalArgumentException("房间码必须为 6 位数字");
    }

    public static LanInvitation parse(String text) {
        String value = text == null ? "" : text.trim();
        int separator = value.lastIndexOf('#');
        int colon = value.lastIndexOf(':', separator);
        if (separator <= 0 || colon <= 0 || colon >= separator - 1)
            throw new IllegalArgumentException("请输入 房主IP:端口#房间码，例如 192.168.137.1:19090#836204");
        try {
            return new LanInvitation(value.substring(0, colon).trim(),
                    Integer.parseInt(value.substring(colon + 1, separator).trim()),
                    value.substring(separator + 1).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("邀请地址中的端口不正确");
        }
    }

    @Override public String toString() { return host + ":" + port + "#" + roomCode; }
}

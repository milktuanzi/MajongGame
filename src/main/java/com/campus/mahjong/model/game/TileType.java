package com.campus.mahjong.model.game;

import java.util.ArrayList;
import java.util.List;

/** 一副标准麻将的 34 种牌面；每种四张，共 136 张。 */
public enum TileType {
    MAN_1("一万", Suit.MAN, 1), MAN_2("二万", Suit.MAN, 2), MAN_3("三万", Suit.MAN, 3),
    MAN_4("四万", Suit.MAN, 4), MAN_5("五万", Suit.MAN, 5), MAN_6("六万", Suit.MAN, 6),
    MAN_7("七万", Suit.MAN, 7), MAN_8("八万", Suit.MAN, 8), MAN_9("九万", Suit.MAN, 9),
    PIN_1("一筒", Suit.PIN, 1), PIN_2("二筒", Suit.PIN, 2), PIN_3("三筒", Suit.PIN, 3),
    PIN_4("四筒", Suit.PIN, 4), PIN_5("五筒", Suit.PIN, 5), PIN_6("六筒", Suit.PIN, 6),
    PIN_7("七筒", Suit.PIN, 7), PIN_8("八筒", Suit.PIN, 8), PIN_9("九筒", Suit.PIN, 9),
    SOU_1("一条", Suit.SOU, 1), SOU_2("二条", Suit.SOU, 2), SOU_3("三条", Suit.SOU, 3),
    SOU_4("四条", Suit.SOU, 4), SOU_5("五条", Suit.SOU, 5), SOU_6("六条", Suit.SOU, 6),
    SOU_7("七条", Suit.SOU, 7), SOU_8("八条", Suit.SOU, 8), SOU_9("九条", Suit.SOU, 9),
    EAST("东", Suit.HONOR, 0), SOUTH("南", Suit.HONOR, 0), WEST("西", Suit.HONOR, 0),
    NORTH("北", Suit.HONOR, 0), RED("中", Suit.HONOR, 0), GREEN("发财", Suit.HONOR, 0),
    WHITE("白板", Suit.HONOR, 0);

    public enum Suit { MAN, PIN, SOU, HONOR }

    private final String displayName;
    private final Suit suit;
    private final int rank;

    TileType(String displayName, Suit suit, int rank) {
        this.displayName = displayName;
        this.suit = suit;
        this.rank = rank;
    }

    public String displayName() { return displayName; }
    public Suit suit() { return suit; }
    public int rank() { return rank; }
    public boolean suited() { return suit != Suit.HONOR; }

    public static TileType fromDisplayName(String name) {
        for (TileType tile : values()) if (tile.displayName.equals(name)) return tile;
        throw new IllegalArgumentException("未知麻将牌: " + name);
    }

    public static List<TileType> fullWall() {
        List<TileType> wall = new ArrayList<>(136);
        for (TileType tile : values()) for (int copy = 0; copy < 4; copy++) wall.add(tile);
        return wall;
    }
}

package com.campus.mahjong.view.component;

import com.campus.mahjong.model.game.TileType;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** 精确绘制牌面并用多层底座表现厚度，避免图片牌面出现错字。 */
public final class MahjongTileView extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DRAWN = PseudoClass.getPseudoClass("drawn");
    private final TileType tile;

    public MahjongTileView(TileType tile, boolean compact) {
        this.tile = tile;
        getStyleClass().addAll("mahjong-tile", compact ? "tile-compact" : "tile-hand");
        setAlignment(Pos.TOP_CENTER);
        setMinSize(compact ? 32 : 52, compact ? 44 : 74);
        setPrefSize(compact ? 32 : 52, compact ? 44 : 74);
        setMaxSize(compact ? 32 : 52, compact ? 44 : 74);

        Rectangle depth = rounded(compact ? 29 : 47, compact ? 40 : 67, 6);
        depth.getStyleClass().add("tile-depth");
        depth.setTranslateY(compact ? 3 : 5);
        Rectangle face = rounded(compact ? 29 : 47, compact ? 40 : 67, 6);
        face.getStyleClass().add("tile-face");
        StackPane artwork = new StackPane(createArtwork(tile, compact));
        artwork.setMouseTransparent(true);
        getChildren().addAll(depth, face, artwork);
        setAccessibleText(tile.displayName());
    }

    public TileType tile() { return tile; }
    public void setSelectedState(boolean selected) { pseudoClassStateChanged(SELECTED, selected); }
    public void setDrawn(boolean drawn) { pseudoClassStateChanged(DRAWN, drawn); }

    private static Rectangle rounded(double width, double height, double radius) {
        Rectangle rectangle = new Rectangle(width, height);
        rectangle.setArcWidth(radius * 2);
        rectangle.setArcHeight(radius * 2);
        return rectangle;
    }

    private static Pane createArtwork(TileType tile, boolean compact) {
        double scale = compact ? .66 : 1.0;
        return switch (tile.suit()) {
            case MAN -> manFace(tile.rank(), scale);
            case PIN -> pinFace(tile.rank(), scale);
            case SOU -> bambooFace(tile.rank(), scale);
            case HONOR -> honorFace(tile, scale);
        };
    }

    private static Pane manFace(int rank, double scale) {
        Label number = new Label("一二三四五六七八九".substring(rank - 1, rank));
        number.getStyleClass().add("tile-man-number");
        Label suit = new Label("萬");
        suit.getStyleClass().add("tile-man-suit");
        VBox box = new VBox(-3 * scale, number, suit);
        box.setAlignment(Pos.CENTER);
        box.setScaleX(scale);
        box.setScaleY(scale);
        return box;
    }

    private static Pane honorFace(TileType tile, double scale) {
        String text = switch (tile) {
            case GREEN -> "發";
            case WHITE -> "□";
            default -> tile.displayName();
        };
        Label label = new Label(text);
        label.getStyleClass().addAll("tile-honor", switch (tile) {
            case RED -> "honor-red";
            case GREEN -> "honor-green";
            case WHITE -> "honor-white";
            default -> "honor-wind";
        });
        label.setScaleX(scale);
        label.setScaleY(scale);
        StackPane pane = new StackPane(label);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private static Pane pinFace(int rank, double scale) {
        GridPane grid = symbolGrid();
        int[][] cells = cellsFor(rank);
        for (int i = 0; i < cells.length; i++) {
            Circle pip = new Circle(3.7, i % 3 == 0 ? Color.web("#dc5749") : Color.web("#2f8b68"));
            pip.setStroke(Color.web("#185e78"));
            pip.setStrokeWidth(.8);
            grid.add(pip, cells[i][1], cells[i][0]);
        }
        grid.setScaleX(scale);
        grid.setScaleY(scale);
        return grid;
    }

    private static Pane bambooFace(int rank, double scale) {
        GridPane grid = symbolGrid();
        int[][] cells = cellsFor(rank);
        for (int i = 0; i < cells.length; i++) {
            Rectangle stem = new Rectangle(3.6, 11, i % 4 == 0 ? Color.web("#df5b4c") : Color.web("#31895d"));
            stem.setArcWidth(3);
            stem.setArcHeight(3);
            stem.setRotate((i % 2 == 0 ? -1 : 1) * 7);
            grid.add(stem, cells[i][1], cells[i][0]);
        }
        grid.setScaleX(scale);
        grid.setScaleY(scale);
        return grid;
    }

    private static GridPane symbolGrid() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(2.8);
        grid.setVgap(2.8);
        return grid;
    }

    private static int[][] cellsFor(int rank) {
        return switch (rank) {
            case 1 -> new int[][]{{1, 1}};
            case 2 -> new int[][]{{0, 0}, {2, 2}};
            case 3 -> new int[][]{{0, 0}, {1, 1}, {2, 2}};
            case 4 -> new int[][]{{0, 0}, {0, 2}, {2, 0}, {2, 2}};
            case 5 -> new int[][]{{0, 0}, {0, 2}, {1, 1}, {2, 0}, {2, 2}};
            case 6 -> new int[][]{{0, 0}, {0, 2}, {1, 0}, {1, 2}, {2, 0}, {2, 2}};
            case 7 -> new int[][]{{0, 0}, {0, 2}, {1, 1}, {1, 0}, {1, 2}, {2, 0}, {2, 2}};
            case 8 -> new int[][]{{0, 0}, {0, 2}, {1, 0}, {1, 2}, {2, 0}, {2, 2}, {0, 1}, {2, 1}};
            default -> new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 0}, {1, 1}, {1, 2}, {2, 0}, {2, 1}, {2, 2}};
        };
    }
}

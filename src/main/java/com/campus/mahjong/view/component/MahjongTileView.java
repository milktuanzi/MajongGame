package com.campus.mahjong.view.component;

import com.campus.mahjong.model.game.TileType;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;

/** 以统一坐标重新绘制全部牌面；原生图形，不依赖截图切片。 */
public final class MahjongTileView extends StackPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DRAWN = PseudoClass.getPseudoClass("drawn");
    private static final Color BLUE = Color.web("#304e83");
    private static final Color RED = Color.web("#b9443d");
    private static final Color GREEN = Color.web("#27805d");
    private final TileType tile;

    public MahjongTileView(TileType tile, boolean compact) {
        this.tile = tile;
        getStyleClass().addAll("mahjong-tile", compact ? "tile-compact" : "tile-hand");
        setAlignment(Pos.TOP_CENTER);
        double width = compact ? 32 : 64, height = compact ? 44 : 88;
        double faceWidth = compact ? 29 : 60, faceHeight = compact ? 40 : 82;
        setMinSize(width, height); setPrefSize(width, height); setMaxSize(width, height);
        Rectangle depth = rounded(faceWidth, faceHeight);
        depth.getStyleClass().add("tile-depth"); depth.setTranslateY(compact ? 3 : 5);
        Rectangle face = rounded(faceWidth, faceHeight); face.getStyleClass().add("tile-face");
        Canvas artwork = new Canvas(faceWidth, faceHeight);
        artwork.setMouseTransparent(true);
        GraphicsContext g = artwork.getGraphicsContext2D();
        g.scale(faceWidth / 100, faceHeight / 140);
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        switch (tile.suit()) {
            case MAN -> { text(g, "一二三四五六七八九".substring(tile.rank() - 1, tile.rank()), 50, 40, 59, BLUE); text(g, "萬", 50, 100, 61, RED); }
            case PIN -> circles(g, tile.rank());
            case SOU -> bamboo(g, tile.rank());
            case HONOR -> {
                if (tile == TileType.WHITE) {
                    g.setStroke(BLUE); g.setLineWidth(4); g.strokeRoundRect(23, 28, 54, 84, 5, 5);
                    g.setLineWidth(1.8); g.strokeRoundRect(30, 35, 40, 70, 3, 3);
                } else text(g, tile == TileType.GREEN ? "發" : tile.displayName(), 50, 72, 72,
                        tile == TileType.RED ? RED : tile == TileType.GREEN ? GREEN : BLUE);
            }
        }
        getChildren().addAll(depth, face, artwork);
        setAccessibleText(tile.displayName());
    }

    public TileType tile() { return tile; }
    public void setSelectedState(boolean selected) { pseudoClassStateChanged(SELECTED, selected); }
    public void setDrawn(boolean drawn) { pseudoClassStateChanged(DRAWN, drawn); }

    private static Rectangle rounded(double width, double height) {
        Rectangle shape = new Rectangle(width, height); shape.setArcWidth(9); shape.setArcHeight(9); return shape;
    }

    private static void text(GraphicsContext g, String value, double x, double y, double size, Color color) {
        g.setFill(color); g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
        g.setFont(Font.font("Kaiti SC", FontWeight.BOLD, size)); g.fillText(value, x, y, 88);
    }

    private static void ring(GraphicsContext g, double x, double y, double radius, Color color) {
        g.setStroke(color); g.setLineWidth(2.8);
        g.strokeOval(x-radius, y-radius, radius*2, radius*2);
        g.setLineWidth(2); g.strokeOval(x-radius*.59, y-radius*.59, radius*1.18, radius*1.18);
        g.setFill(color); g.fillOval(x-2, y-2, 4, 4);
    }

    private static void circles(GraphicsContext g, int rank) {
        if (rank == 1) {
            ring(g, 50, 70, 32, GREEN);
            for (int i=0;i<12;i++) {
                double a = i*Math.PI/6;
                g.setStroke(GREEN); g.setLineWidth(3);
                g.strokeLine(50+21*Math.cos(a),70+21*Math.sin(a),50+27*Math.cos(a),70+27*Math.sin(a));
            }
            ring(g, 50, 70, 12, RED); return;
        }
        double[][] points = positions(rank);
        for (int i=0;i<points.length;i++) {
            Color color = switch(rank) {
                case 2 -> i==0 ? BLUE : GREEN;
                case 3 -> i==0 ? BLUE : i==1 ? RED : GREEN;
                case 4 -> i==0 || i==3 ? BLUE : GREEN;
                case 5 -> i==2 ? RED : BLUE;
                case 6, 7 -> i < rank-4 ? GREEN : RED;
                case 8 -> BLUE;
                default -> i<3 ? BLUE : i<6 ? RED : GREEN;
            };
            ring(g, points[i][0], points[i][1], rank <= 5 ? 14 : 11, color);
        }
    }

    private static void stem(GraphicsContext g, double x, double y, Color color, double length) {
        g.setStroke(color); g.setLineWidth(7); g.strokeLine(x,y-length/2,x,y+length/2);
        g.setLineWidth(2.5); g.strokeLine(x-4,y-length/2,x+4,y-length/2); g.strokeLine(x-4,y,x+4,y); g.strokeLine(x-4,y+length/2,x+4,y+length/2);
        g.setStroke(Color.web("#fdfefb")); g.setLineWidth(1.2);g.strokeLine(x,y-length/2+3,x,y+length/2-3);
    }

    private static void bamboo(GraphicsContext g, int rank) {
        if (rank == 1) { bird(g); return; }
        if (rank == 8) {
            g.setStroke(GREEN); g.setLineWidth(5);
            for (int row=0;row<2;row++) {
                double y=40+row*55;
                stem(g,25,y,GREEN,27);stem(g,75,y,GREEN,27);
                g.setStroke(GREEN);g.setLineWidth(5);
                double direction = row == 0 ? 1 : -1;
                g.strokeLine(25,y+9*direction,50,y-12*direction);g.strokeLine(50,y-12*direction,75,y+9*direction);
            }
            return;
        }
        double[][] points = rank == 3 ? new double[][]{{50,35},{28,99},{72,99}}
                : rank == 7 ? new double[][]{{50,22},{25,64},{50,64},{75,64},{25,108},{50,108},{75,108}}
                : positions(rank);
        for(int i=0;i<points.length;i++) {
            Color color = rank==5 && i==2 || rank==7 && i==0 ? RED : rank==9 ? (i%3==0?BLUE:i%3==1?RED:GREEN) : GREEN;
            stem(g,points[i][0],points[i][1],color,rank<=4?30:23);
        }
    }

    private static void bird(GraphicsContext g) {
        g.setStroke(GREEN);g.setLineWidth(3.5);
        g.beginPath();g.moveTo(28,80);g.bezierCurveTo(10,45,42,40,49,66);g.bezierCurveTo(60,79,77,64,75,54);
        g.bezierCurveTo(94,78,75,102,45,91);g.bezierCurveTo(32,91,25,85,28,80);g.stroke();
        g.strokeOval(42,27,25,25);g.strokeLine(64,37,80,33);g.strokeLine(80,33,65,43);
        g.setFill(RED);g.fillOval(53,34,5,5);
        g.setStroke(GREEN);g.strokeLine(48,29,43,21);g.strokeLine(43,21,57,24);
        for(int i=0;i<4;i++){g.beginPath();g.moveTo(35+i*6,58);g.quadraticCurveTo(35+i*6,82,55+i*4,82);g.stroke();}
        g.strokeLine(45,92,35,121);g.strokeLine(52,93,48,126);g.strokeLine(59,92,62,118);
        g.strokeLine(24,98,78,98);
    }

    private static double[][] positions(int rank) {
        return switch(rank) {
            case 2 -> new double[][]{{50,42},{50,98}};
            case 3 -> new double[][]{{27,37},{50,70},{73,103}};
            case 4 -> new double[][]{{28,43},{72,43},{28,97},{72,97}};
            case 5 -> new double[][]{{25,35},{75,35},{50,70},{25,105},{75,105}};
            case 6 -> new double[][]{{32,33},{68,33},{32,70},{68,70},{32,107},{68,107}};
            case 7 -> new double[][]{{25,29},{50,42},{75,55},{32,84},{68,84},{32,113},{68,113}};
            case 8 -> new double[][]{{32,25},{68,25},{32,55},{68,55},{32,85},{68,85},{32,115},{68,115}};
            default -> new double[][]{{23,32},{50,32},{77,32},{23,70},{50,70},{77,70},{23,108},{50,108},{77,108}};
        };
    }
}

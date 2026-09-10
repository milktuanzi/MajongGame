package com.campus.mahjong.view.component;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** 四家共用的公开信息：胡牌贴图、昵称、独立缺门和实时积分。 */
public final class PlayerInfoView extends HBox {
    private static final Image SELF_DRAW = asset("win-self-draw.png");
    private static final Image DISCARD_WIN = asset("win-discard.png");
    private final ImageView winImage = new ImageView();
    private final Label name = new Label();
    private final Label missing = new Label();
    private final Label score = new Label();
    private final Tooltip tooltip = new Tooltip();

    public PlayerInfoView() {
        super(8);
        setAlignment(Pos.CENTER);
        setPrefSize(300, 74); setMaxSize(300, 74);
        getStyleClass().add("player-info");
        winImage.setFitWidth(86); winImage.setFitHeight(55); winImage.setPreserveRatio(true);
        name.getStyleClass().add("player-info-name");
        name.setMaxWidth(118);
        missing.getStyleClass().add("player-info-missing");
        missing.setMinWidth(48); missing.setAlignment(Pos.CENTER);
        score.getStyleClass().add("player-info-score");
        HBox heading = new HBox(7, name, missing); heading.setAlignment(Pos.CENTER_LEFT);
        VBox identity = new VBox(3, heading, score); identity.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(winImage, identity);
        Tooltip.install(this, tooltip);
    }

    public void update(String caption, String missingText, long points, String winType) {
        name.setText(caption);
        missing.setText(missingText); missing.setVisible(!missingText.isEmpty()); missing.setManaged(!missingText.isEmpty());
        score.setText("积分  " + (points > 0 ? "+" : "") + points);
        score.pseudoClassStateChanged(PseudoClass.getPseudoClass("negative"), points < 0);
        winImage.setImage("自摸".equals(winType) ? SELF_DRAW : "点炮".equals(winType) ? DISCARD_WIN : null);
        winImage.setVisible(winImage.getImage() != null);
        winImage.setManaged(winImage.isVisible());
        String description = caption + "，" + missingText + "，" + score.getText()
                + (winType.isEmpty() ? "" : "，" + (winType.equals("点炮") ? "点炮胡" : winType));
        setAccessibleText(description); tooltip.setText(description);
    }

    private static Image asset(String name) {
        return new Image(PlayerInfoView.class.getResource("/com/campus/mahjong/view/images/table/" + name).toExternalForm(), 200, 120, true, true);
    }
}

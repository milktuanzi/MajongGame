package com.campus.mahjong.controller.page;

import com.campus.mahjong.controller.navigation.AppNavigator;
import javafx.fxml.FXML;

public final class SettlementController {
    @FXML private void nextRound() { AppNavigator.game(); }
    @FXML private void returnHome() { AppNavigator.home(); }
}

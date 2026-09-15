package com.campus.mahjong.controller.page;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameControllerDrawAnimationTest {
    @Test
    void newDrawAnimatesForActivePlayer() {
        assertTrue(GameController.shouldAnimateDraw(true, false, false,
                "1:80:五万", "1:81:二筒"));
    }

    @Test
    void selfDrawWinningTileDoesNotAnimateWhenOtherPlayersAdvanceTheWall() {
        assertFalse(GameController.shouldAnimateDraw(true, false, true,
                "1:79:五万", "1:80:五万"));
    }

    @Test
    void unchangedDrawOrReducedMotionDoesNotAnimate() {
        assertFalse(GameController.shouldAnimateDraw(true, false, false,
                "1:80:五万", "1:80:五万"));
        assertFalse(GameController.shouldAnimateDraw(true, true, false,
                "1:79:五万", "1:80:五万"));
    }
}

package com.campus.mahjong.model.rule;

import com.campus.mahjong.model.game.TileType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardHandEvaluatorTest {
    private final StandardHandEvaluator evaluator = new StandardHandEvaluator();

    @Test
    void recognizesFourMeldsAndPair() {
        List<TileType> hand = List.of(
                TileType.MAN_1, TileType.MAN_2, TileType.MAN_3,
                TileType.MAN_4, TileType.MAN_5, TileType.MAN_6,
                TileType.PIN_2, TileType.PIN_3, TileType.PIN_4,
                TileType.RED, TileType.RED, TileType.RED,
                TileType.EAST, TileType.EAST);
        assertTrue(evaluator.isWinningHand(hand, 0));
    }

    @Test
    void recognizesSevenPairsAndRejectsBrokenHand() {
        List<TileType> sevenPairs = List.of(
                TileType.MAN_1, TileType.MAN_1, TileType.MAN_3, TileType.MAN_3,
                TileType.PIN_2, TileType.PIN_2, TileType.PIN_7, TileType.PIN_7,
                TileType.SOU_4, TileType.SOU_4, TileType.EAST, TileType.EAST,
                TileType.RED, TileType.RED);
        assertTrue(evaluator.isWinningHand(sevenPairs, 0));
        assertFalse(evaluator.isWinningHand(sevenPairs.subList(0, 13), 0));
    }
}

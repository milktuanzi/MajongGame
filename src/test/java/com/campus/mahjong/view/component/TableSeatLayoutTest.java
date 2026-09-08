package com.campus.mahjong.view.component;

import com.campus.mahjong.model.common.MahjongTypes.Seat;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableSeatLayoutTest {
    @Test
    void everyClientKeepsSelfBottomAndOpponentsCounterClockwise() {
        for (Seat local : Seat.values()) {
            EnumSet<Seat> displayed = EnumSet.noneOf(Seat.class);
            for (TableSeatLayout.Position position : TableSeatLayout.Position.values()) {
                displayed.add(TableSeatLayout.seatAt(local, position));
            }
            assertEquals(EnumSet.allOf(Seat.class), displayed);
            assertEquals(local, TableSeatLayout.seatAt(local, TableSeatLayout.Position.BOTTOM));
            assertEquals(Seat.values()[(local.ordinal() + 1) % 4],
                    TableSeatLayout.seatAt(local, TableSeatLayout.Position.RIGHT));
        }
    }
}

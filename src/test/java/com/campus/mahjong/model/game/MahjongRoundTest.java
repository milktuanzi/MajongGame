package com.campus.mahjong.model.game;

import com.campus.mahjong.model.common.MahjongTypes.FriendRoomSettings;
import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import com.campus.mahjong.model.common.MahjongTypes.Seat;
import com.campus.mahjong.model.common.MahjongTypes.PlayerActionType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MahjongRoundTest {
    private FriendRoomSettings settings() {
        return new FriendRoomSettings(ModeCode.NORTHERN, 2, Optional.of(128), 8, false, "");
    }

    @Test
    void dealsCorrectNumberOfTilesAndBuildsFullWall() {
        MahjongRound round = MahjongRound.start(settings(), 42L);
        assertEquals(14, round.hand(Seat.EAST).size());
        assertEquals(13, round.hand(Seat.SOUTH).size());
        assertEquals(83, round.wallRemaining());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
    }

    @Test
    void advancesAfterAllPlayersPassAndRejectsStaleRevision() {
        MahjongRound round = MahjongRound.start(settings(), 7L);
        TileType discarded = round.hand(Seat.EAST).get(0);
        round.discard(Seat.EAST, discarded, 0);
        assertEquals(RoundPhase.WAITING_FOR_CLAIMS, round.phase());
        assertThrows(IllegalStateException.class, () -> round.discard(Seat.EAST, discarded, 0));
        round.passRemainingClaims();
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(14, round.hand(Seat.SOUTH).size());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
    }

    @Test
    void drawnTileRemainsSeparateUntilAConfirmedDiscardReorganizesHand() {
        MahjongRound round = MahjongRound.start(settings(), 23L);
        TileType drawn = round.drawnTile(Seat.EAST).orElseThrow();
        TileType organized = round.organizedHand(Seat.EAST).get(0);

        assertEquals(13, round.organizedHand(Seat.EAST).size());
        assertEquals(14, round.hand(Seat.EAST).size());
        assertEquals(drawn, round.hand(Seat.EAST).get(13));

        round.discard(Seat.EAST, organized, false, round.revision());
        assertEquals(13, round.organizedHand(Seat.EAST).size());
        assertEquals(0, round.drawnTile(Seat.EAST).stream().count());
    }

    @Test
    void allClaimantsCanRespondAgainstSameClaimWindowRevision() {
        MahjongRound round = MahjongRound.start(settings(), 9L);
        round.discard(Seat.EAST, round.hand(Seat.EAST).get(0), round.revision());
        long claimRevision = round.revision();
        round.submitClaim(Seat.SOUTH, PlayerActionType.PASS, claimRevision);
        round.submitClaim(Seat.WEST, PlayerActionType.PASS, claimRevision);
        round.submitClaim(Seat.NORTH, PlayerActionType.PASS, claimRevision);
        assertEquals(Seat.SOUTH, round.currentTurn());
        assertEquals(RoundPhase.WAITING_FOR_DISCARD, round.phase());
    }

    @Test
    void wallContainsExactlyFourCopiesOfEveryTile() {
        assertEquals(136, TileType.fullWall().size());
        for (TileType tile : TileType.values()) {
            assertEquals(4, TileType.fullWall().stream().filter(tile::equals).count());
        }
    }
}

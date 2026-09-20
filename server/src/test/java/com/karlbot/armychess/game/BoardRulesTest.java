package com.karlbot.armychess.game;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BoardRulesTest {
    @Test
    void engineerCanTurnAcrossRailwayButOtherPiecesCannot() {
        Position from = new Position(8, 15);
        Position aroundCorner = new Position(15, 8);
        Map<Position, PieceState> board = new HashMap<>();
        PieceState sapper = new PieceState("opaque", PieceType.SAPPER, 0, from);
        board.put(from, sapper);

        assertThat(BoardRules.isValidPath(sapper, aroundCorner, board)).isTrue();
        assertThat(BoardRules.isValidPath(new PieceState("opaque-2", PieceType.CAPTAIN, 0, from),
                aroundCorner, board)).isFalse();
    }

    @Test
    void campsAndHeadquartersMatchClassicFourPlayerBoard() {
        assertThat(BoardRules.isCamp(new Position(8, 13))).isTrue();
        assertThat(BoardRules.isHeadquarters(new Position(7, 16))).isTrue();
        assertThat(BoardRules.territoryOwner(new Position(9, 16))).isEqualTo(0);
        assertThat(BoardRules.territoryOwner(new Position(16, 9))).isEqualTo(3);
    }
}

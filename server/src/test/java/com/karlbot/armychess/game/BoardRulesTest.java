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

    @Test
    void railwayPieceCannotBypassBlockerAtCurvedJunction() {
        Position from = new Position(10, 12);
        PieceState bomb = new PieceState("bomb", PieceType.BOMB, 0, from);
        for (Position blockedJunction : new Position[]{new Position(10, 11), new Position(11, 10)}) {
            PieceState marshal = new PieceState("marshal", PieceType.MARSHAL, 0, blockedJunction);
            Map<Position, PieceState> board = new HashMap<>();
            board.put(bomb.position(), bomb);
            board.put(marshal.position(), marshal);

            assertThat(BoardRules.isValidPath(bomb, new Position(12, 10), board)).isFalse();
        }
    }
}

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
    void engineerCanUseAnAlternateRailwayRouteAroundOneBlocker() {
        Position from = new Position(10, 12);
        PieceState sapper = new PieceState("sapper", PieceType.SAPPER, 0, from);
        PieceState blocker = new PieceState("blocker", PieceType.MARSHAL, 0, new Position(10, 11));
        Map<Position, PieceState> board = new HashMap<>();
        board.put(sapper.position(), sapper);
        board.put(blocker.position(), blocker);

        assertThat(BoardRules.legalDestinations(sapper, board)).contains(new Position(12, 10));
        assertThat(BoardRules.movePath(sapper, new Position(12, 10), board))
                .startsWith(from)
                .endsWith(new Position(12, 10))
                .doesNotContain(blocker.position())
                .hasSizeGreaterThan(10);
    }

    @Test
    void engineerCannotPassWhenEveryRailwayExitIsBlocked() {
        Position from = new Position(10, 12);
        PieceState sapper = new PieceState("sapper", PieceType.SAPPER, 0, from);
        PieceState north = new PieceState("north", PieceType.MARSHAL, 0, new Position(10, 11));
        PieceState south = new PieceState("south", PieceType.GENERAL, 0, new Position(10, 13));
        Map<Position, PieceState> board = new HashMap<>();
        board.put(sapper.position(), sapper);
        board.put(north.position(), north);
        board.put(south.position(), south);

        assertThat(BoardRules.legalDestinations(sapper, board)).doesNotContain(new Position(12, 10));
    }

    @Test
    void engineerCanCaptureARailwayBlockerButCannotPassThroughIt() {
        Position from = new Position(10, 12);
        PieceState sapper = new PieceState("sapper", PieceType.SAPPER, 0, from);
        PieceState enemy = new PieceState("enemy", PieceType.CAPTAIN, 1, new Position(10, 11));
        PieceState rearBlocker = new PieceState("rear", PieceType.GENERAL, 0, new Position(10, 13));
        Map<Position, PieceState> board = new HashMap<>();
        board.put(sapper.position(), sapper);
        board.put(enemy.position(), enemy);
        board.put(rearBlocker.position(), rearBlocker);

        assertThat(BoardRules.legalDestinations(sapper, board))
                .contains(new Position(10, 11))
                .doesNotContain(new Position(10, 10));
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

    @Test
    void ordinaryRailwayPathIncludesEveryTraversedJunction() {
        Position from = new Position(10, 12);
        PieceState captain = new PieceState("captain", PieceType.CAPTAIN, 0, from);
        Map<Position, PieceState> board = new HashMap<>();
        board.put(from, captain);

        assertThat(BoardRules.movePath(captain, new Position(12, 10), board))
                .containsExactly(
                        new Position(10, 12),
                        new Position(10, 11),
                        new Position(11, 10),
                        new Position(12, 10));
    }
}

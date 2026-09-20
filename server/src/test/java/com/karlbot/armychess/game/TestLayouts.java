package com.karlbot.armychess.game;

import java.util.ArrayList;
import java.util.List;

final class TestLayouts {
    private static final List<PieceType> REMAINING = List.of(
            PieceType.MARSHAL, PieceType.GENERAL,
            PieceType.M_GENERAL, PieceType.M_GENERAL,
            PieceType.BRIGADIER, PieceType.BRIGADIER,
            PieceType.COLONEL, PieceType.COLONEL,
            PieceType.MAJOR, PieceType.MAJOR,
            PieceType.CAPTAIN, PieceType.CAPTAIN, PieceType.CAPTAIN,
            PieceType.LIEUTENANT, PieceType.LIEUTENANT, PieceType.LIEUTENANT,
            PieceType.SAPPER, PieceType.SAPPER, PieceType.SAPPER
    );

    private TestLayouts() {}

    static List<PiecePlacement> valid(int player) {
        List<Position> open = new ArrayList<>();
        for (int y = 0; y < BoardRules.SIZE; y++) {
            for (int x = 0; x < BoardRules.SIZE; x++) {
                Position position = new Position(x, y);
                if (BoardRules.territoryOwner(position) == player && !BoardRules.isCamp(position)) open.add(position);
            }
        }
        List<PiecePlacement> placements = new ArrayList<>();
        place(placements, open, PieceType.FLAG, BoardRules::isHeadquarters);
        for (int i = 0; i < 3; i++) place(placements, open, PieceType.LANDMINE,
                position -> BoardRules.isLastTwoRows(player, position));
        for (int i = 0; i < 2; i++) place(placements, open, PieceType.BOMB,
                position -> !BoardRules.isFirstRow(player, position));
        for (PieceType type : REMAINING) place(placements, open, type, position -> true);
        return List.copyOf(placements);
    }

    private static void place(List<PiecePlacement> placements, List<Position> open, PieceType type,
                              java.util.function.Predicate<Position> predicate) {
        Position position = open.stream().filter(predicate).findFirst().orElseThrow();
        open.remove(position);
        placements.add(new PiecePlacement(type, position));
    }
}

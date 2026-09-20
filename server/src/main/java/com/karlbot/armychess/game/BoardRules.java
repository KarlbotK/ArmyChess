package com.karlbot.armychess.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class BoardRules {
    public static final int SIZE = 17;

    private static final Set<Position> CAMPS = new HashSet<>();
    private static final Set<Position> RAILS = new HashSet<>();
    private static final Set<Position> HEADQUARTERS = new HashSet<>();
    private static final List<RouteDefinition> CURVED_ROUTES = List.of(
            new RouteDefinition(BoardRules::northRight, BoardRules::eastTop, new Position(10, 5), new Position(11, 6)),
            new RouteDefinition(BoardRules::eastTop, BoardRules::northRight, new Position(11, 6), new Position(10, 5)),
            new RouteDefinition(BoardRules::eastBottom, BoardRules::southRight, new Position(11, 10), new Position(10, 11)),
            new RouteDefinition(BoardRules::southRight, BoardRules::eastBottom, new Position(10, 11), new Position(11, 10)),
            new RouteDefinition(BoardRules::southLeft, BoardRules::westBottom, new Position(6, 11), new Position(5, 10)),
            new RouteDefinition(BoardRules::westBottom, BoardRules::southLeft, new Position(5, 10), new Position(6, 11)),
            new RouteDefinition(BoardRules::westTop, BoardRules::northLeft, new Position(5, 6), new Position(6, 5)),
            new RouteDefinition(BoardRules::northLeft, BoardRules::westTop, new Position(6, 5), new Position(5, 6))
    );

    static {
        addCamps(
                7, 2, 9, 2, 8, 3, 7, 4, 9, 4,
                7, 12, 9, 12, 8, 13, 7, 14, 9, 14,
                2, 7, 2, 9, 3, 8, 4, 7, 4, 9,
                12, 7, 12, 9, 13, 8, 14, 7, 14, 9
        );
        addHeadquarters(7, 0, 9, 0, 7, 16, 9, 16, 0, 7, 0, 9, 16, 7, 16, 9);

        for (int y = 6; y <= 10; y++) {
            addRail(6, y); addRail(8, y); addRail(10, y);
        }
        for (int x = 6; x <= 10; x++) {
            addRail(x, 6); addRail(x, 8); addRail(x, 10);
            addRail(x, 5); addRail(x, 1); addRail(x, 11); addRail(x, 15);
        }
        for (int y = 1; y <= 5; y++) {
            addRail(6, y); addRail(10, y);
        }
        for (int y = 11; y <= 15; y++) {
            addRail(6, y); addRail(10, y);
        }
        for (int y = 6; y <= 10; y++) {
            addRail(5, y); addRail(1, y); addRail(11, y); addRail(15, y);
        }
        for (int x = 1; x <= 5; x++) {
            addRail(x, 6); addRail(x, 10);
        }
        for (int x = 11; x <= 15; x++) {
            addRail(x, 6); addRail(x, 10);
        }
    }

    private BoardRules() {}

    private static void addCamps(int... values) {
        for (int i = 0; i < values.length; i += 2) CAMPS.add(new Position(values[i], values[i + 1]));
    }

    private static void addHeadquarters(int... values) {
        for (int i = 0; i < values.length; i += 2) HEADQUARTERS.add(new Position(values[i], values[i + 1]));
    }

    private static void addRail(int x, int y) { RAILS.add(new Position(x, y)); }

    public static boolean isCamp(Position p) { return CAMPS.contains(p); }
    public static boolean isRail(Position p) { return RAILS.contains(p); }
    public static boolean isHeadquarters(Position p) { return HEADQUARTERS.contains(p); }

    public static boolean isValidPoint(int x, int y) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return false;
        if (x >= 6 && x <= 10 && ((y >= 0 && y <= 5) || (y >= 11 && y <= 16))) return true;
        if (y >= 6 && y <= 10 && ((x >= 0 && x <= 5) || (x >= 11 && x <= 16))) return true;
        return x >= 6 && x <= 10 && y >= 6 && y <= 10 && RAILS.contains(new Position(x, y));
    }

    public static boolean isStation(Position p) {
        if (!isValidPoint(p.x(), p.y())) return false;
        if (p.x() >= 6 && p.x() <= 10 && p.y() >= 6 && p.y() <= 10) {
            return p.x() % 2 == 0 && p.y() % 2 == 0;
        }
        return true;
    }

    public static int territoryOwner(Position p) {
        if (p.y() >= 11 && p.y() <= 16 && p.x() >= 6 && p.x() <= 10) return 0;
        if (p.x() >= 0 && p.x() <= 5 && p.y() >= 6 && p.y() <= 10) return 1;
        if (p.y() >= 0 && p.y() <= 5 && p.x() >= 6 && p.x() <= 10) return 2;
        if (p.x() >= 11 && p.x() <= 16 && p.y() >= 6 && p.y() <= 10) return 3;
        return -1;
    }

    public static boolean isLastTwoRows(int player, Position p) {
        return switch (player) {
            case 0 -> p.y() >= 15;
            case 1 -> p.x() <= 1;
            case 2 -> p.y() <= 1;
            case 3 -> p.x() >= 15;
            default -> false;
        };
    }

    public static boolean isFirstRow(int player, Position p) {
        return switch (player) {
            case 0 -> p.y() == 11;
            case 1 -> p.x() == 5;
            case 2 -> p.y() == 5;
            case 3 -> p.x() == 11;
            default -> false;
        };
    }

    public static boolean isTeammate(int a, int b) { return Math.floorMod(a, 2) == Math.floorMod(b, 2); }

    public static boolean isNeighbor(Position a, Position b) {
        if (!isValidPoint(a.x(), a.y()) || !isValidPoint(b.x(), b.y())) return false;
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        if (dx == 0 && dy == 0) return false;
        if ((isCamp(a) || isCamp(b)) && dx <= 1 && dy <= 1) return true;
        if (dx + dy == 1) return !isBlockedPath(a, b);
        return isCornerConnected(a, b);
    }

    private static boolean isBlockedPath(Position a, Position b) {
        if (a.y() == b.y()) {
            int minX = Math.min(a.x(), b.x());
            if ((minX == 5 || minX == 10) && (a.y() == 7 || a.y() == 9)) return true;
        }
        if (a.x() == b.x()) {
            int minY = Math.min(a.y(), b.y());
            if ((minY == 5 || minY == 10) && (a.x() == 7 || a.x() == 9)) return true;
        }
        return false;
    }

    public static boolean isCornerConnected(Position a, Position b) {
        return pair(a, b, 5, 6, 6, 5)
                || pair(a, b, 10, 5, 11, 6)
                || pair(a, b, 11, 10, 10, 11)
                || pair(a, b, 6, 11, 5, 10);
    }

    private static boolean pair(Position a, Position b, int ax, int ay, int bx, int by) {
        return (a.x() == ax && a.y() == ay && b.x() == bx && b.y() == by)
                || (a.x() == bx && a.y() == by && b.x() == ax && b.y() == ay);
    }

    public static List<Position> legalDestinations(PieceState piece, Map<Position, PieceState> board) {
        List<Position> legal = new ArrayList<>();
        SapperSearch sapperSearch = piece.type() == PieceType.SAPPER
                && isRail(piece.position()) && !isHeadquarters(piece.position())
                ? sapperSearch(piece.position(), board)
                : null;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!isValidPoint(x, y)) continue;
                Position target = new Position(x, y);
                if (!isStation(target)) continue;
                PieceState occupant = board.get(target);
                if (occupant != null && (isTeammate(piece.owner(), occupant.owner()) || isCamp(target))) continue;
                boolean validPath = sapperSearch != null && isRail(target)
                        ? sapperSearch.reachable().contains(target)
                        : isValidPath(piece, target, board);
                if (validPath) legal.add(target);
            }
        }
        return legal;
    }

    public static List<Position> movePath(PieceState piece, Position to, Map<Position, PieceState> board) {
        Position from = piece.position();
        if (!isValidPath(piece, to, board)) return List.of();
        if (isNeighbor(from, to)) return List.of(from, to);
        if (piece.type() == PieceType.SAPPER) return sapperPath(from, to, sapperSearch(from, board));
        if (from.x() == to.x() || from.y() == to.y()) return linePath(from, to);
        return curvedPath(from, to, board);
    }

    public static boolean isValidPath(PieceState piece, Position to, Map<Position, PieceState> board) {
        Position from = piece.position();
        if (!isStation(to)) return false;
        if (piece.type() == PieceType.LANDMINE || piece.type() == PieceType.FLAG || isHeadquarters(from)) return false;
        if (isNeighbor(from, to)) return true;
        if (!isRail(from) || !isRail(to)) return false;
        if (piece.type() == PieceType.SAPPER) return sapperSearch(from, board).reachable().contains(to);
        if (from.x() == to.x() || from.y() == to.y()) return pathClear(from, to, board);
        return curveClear(from, to, board);
    }

    private static boolean pathClear(Position from, Position to, Map<Position, PieceState> board) {
        if (isCornerConnected(from, to)) return true;
        int dx = Integer.compare(to.x(), from.x());
        int dy = Integer.compare(to.y(), from.y());
        int x = from.x() + dx;
        int y = from.y() + dy;
        while (x != to.x() || y != to.y()) {
            if (!isValidPoint(x, y)) return false;
            Position current = new Position(x, y);
            if (!isRail(current) || board.containsKey(current)) return false;
            x += dx;
            y += dy;
        }
        return true;
    }

    private static boolean curveClear(Position from, Position to, Map<Position, PieceState> board) {
        return CURVED_ROUTES.stream().anyMatch(definition -> route(
                from, to, board, definition.start(), definition.end(),
                definition.firstCorner(), definition.secondCorner()));
    }

    private static List<Position> curvedPath(Position from, Position to, Map<Position, PieceState> board) {
        for (RouteDefinition definition : CURVED_ROUTES) {
            if (!route(from, to, board, definition.start(), definition.end(),
                    definition.firstCorner(), definition.secondCorner())) continue;
            List<Position> path = new ArrayList<>(linePath(from, definition.firstCorner()));
            path.add(definition.secondCorner());
            List<Position> finalLeg = linePath(definition.secondCorner(), to);
            path.addAll(finalLeg.subList(1, finalLeg.size()));
            return List.copyOf(path);
        }
        return List.of();
    }

    private static List<Position> linePath(Position from, Position to) {
        List<Position> path = new ArrayList<>();
        path.add(from);
        int dx = Integer.compare(to.x(), from.x());
        int dy = Integer.compare(to.y(), from.y());
        int x = from.x();
        int y = from.y();
        while (x != to.x() || y != to.y()) {
            x += dx;
            y += dy;
            path.add(new Position(x, y));
        }
        return List.copyOf(path);
    }

    @FunctionalInterface
    private interface PositionCheck { boolean test(Position p); }

    private record RouteDefinition(
            PositionCheck start,
            PositionCheck end,
            Position firstCorner,
            Position secondCorner
    ) {}

    private static boolean route(Position from, Position to, Map<Position, PieceState> board,
                                 PositionCheck start, PositionCheck end, Position firstCorner, Position secondCorner) {
        return start.test(from) && end.test(to)
                && transitOpen(firstCorner, from, to, board)
                && transitOpen(secondCorner, from, to, board)
                && pathClear(from, firstCorner, board)
                && pathClear(secondCorner, to, board);
    }

    private static boolean transitOpen(Position point, Position from, Position to,
                                       Map<Position, PieceState> board) {
        return point.equals(from) || point.equals(to) || !board.containsKey(point);
    }

    private static boolean northRight(Position p) { return p.x() == 10 && p.y() <= 5; }
    private static boolean eastTop(Position p) { return p.y() == 6 && p.x() >= 11; }
    private static boolean eastBottom(Position p) { return p.y() == 10 && p.x() >= 11; }
    private static boolean southRight(Position p) { return p.x() == 10 && p.y() >= 11; }
    private static boolean southLeft(Position p) { return p.x() == 6 && p.y() >= 11; }
    private static boolean westBottom(Position p) { return p.y() == 10 && p.x() <= 5; }
    private static boolean westTop(Position p) { return p.y() == 6 && p.x() <= 5; }
    private static boolean northLeft(Position p) { return p.x() == 6 && p.y() <= 5; }

    private record SapperSearch(Set<Position> reachable, Map<Position, Position> predecessor) {}

    private static SapperSearch sapperSearch(Position from, Map<Position, PieceState> board) {
        Queue<Position> queue = new ArrayDeque<>();
        Set<Position> visited = new HashSet<>();
        Set<Position> reachable = new HashSet<>();
        Map<Position, Position> predecessor = new HashMap<>();
        queue.add(from);
        visited.add(from);
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        while (!queue.isEmpty()) {
            Position current = queue.remove();
            for (int[] direction : directions) {
                int x = current.x() + direction[0];
                int y = current.y() + direction[1];
                if (!isValidPoint(x, y)) continue;
                Position next = new Position(x, y);
                if (!isRail(next) || !isNeighbor(current, next)) continue;
                addReachable(current, next, board, visited, reachable, predecessor, queue);
            }
            addCorner(current, new Position(10, 5), new Position(11, 6), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(11, 6), new Position(10, 5), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(11, 10), new Position(10, 11), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(10, 11), new Position(11, 10), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(6, 11), new Position(5, 10), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(5, 10), new Position(6, 11), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(5, 6), new Position(6, 5), board, visited, reachable, predecessor, queue);
            addCorner(current, new Position(6, 5), new Position(5, 6), board, visited, reachable, predecessor, queue);
        }
        return new SapperSearch(Set.copyOf(reachable), Map.copyOf(predecessor));
    }

    private static void addCorner(Position current, Position source, Position destination,
                                  Map<Position, PieceState> board, Set<Position> visited,
                                  Set<Position> reachable, Map<Position, Position> predecessor,
                                  Queue<Position> queue) {
        if (current.equals(source)) {
            addReachable(current, destination, board, visited, reachable, predecessor, queue);
        }
    }

    private static void addReachable(Position current, Position next, Map<Position, PieceState> board,
                                     Set<Position> visited, Set<Position> reachable,
                                     Map<Position, Position> predecessor, Queue<Position> queue) {
        if (visited.contains(next)) return;
        visited.add(next);
        reachable.add(next);
        predecessor.put(next, current);
        if (!board.containsKey(next)) queue.add(next);
    }

    private static List<Position> sapperPath(Position from, Position to, SapperSearch search) {
        if (!search.reachable().contains(to)) return List.of();
        List<Position> reversed = new ArrayList<>();
        Position current = to;
        reversed.add(current);
        while (!current.equals(from)) {
            current = search.predecessor().get(current);
            if (current == null) return List.of();
            reversed.add(current);
        }
        List<Position> path = new ArrayList<>();
        for (int index = reversed.size() - 1; index >= 0; index--) path.add(reversed.get(index));
        return List.copyOf(path);
    }
}

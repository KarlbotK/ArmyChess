package com.karlbot.armychess.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameEngine {
    private static final Map<PieceType, Integer> REQUIRED_COUNTS = Map.ofEntries(
            Map.entry(PieceType.MARSHAL, 1),
            Map.entry(PieceType.GENERAL, 1),
            Map.entry(PieceType.M_GENERAL, 2),
            Map.entry(PieceType.BRIGADIER, 2),
            Map.entry(PieceType.COLONEL, 2),
            Map.entry(PieceType.MAJOR, 2),
            Map.entry(PieceType.CAPTAIN, 3),
            Map.entry(PieceType.LIEUTENANT, 3),
            Map.entry(PieceType.SAPPER, 3),
            Map.entry(PieceType.BOMB, 2),
            Map.entry(PieceType.LANDMINE, 3),
            Map.entry(PieceType.FLAG, 1)
    );

    public enum Phase { LAYOUT, PLAYING, FINISHED }

    private final Map<Position, PieceState> board = new HashMap<>();
    private final Set<Integer> submitted = new HashSet<>();
    private final boolean[] alive = {true, true, true, true};
    private Phase phase = Phase.LAYOUT;
    private int currentTurn;
    private long revision = 1;

    public synchronized void submitLayout(int player, List<PiecePlacement> placements) {
        requirePlayer(player);
        if (phase != Phase.LAYOUT) throw new GameRuleException("WRONG_PHASE", "当前不在布阵阶段");
        if (submitted.contains(player)) throw new GameRuleException("LAYOUT_ALREADY_SUBMITTED", "布局已经提交");
        validateLayout(player, placements);

        Map<Position, PieceState> next = new HashMap<>();
        for (PiecePlacement placement : placements) {
            // Random server IDs cannot be correlated with client layout order or a hidden piece type.
            String id = "piece-" + UUID.randomUUID();
            next.put(placement.position(), new PieceState(id, placement.type(), player, placement.position()));
        }
        board.putAll(next);
        submitted.add(player);
        revision++;
        if (submitted.size() == 4) phase = Phase.PLAYING;
    }

    private void validateLayout(int player, List<PiecePlacement> placements) {
        if (placements == null || placements.size() != 25) {
            throw new GameRuleException("INVALID_PIECE_COUNT", "布局必须包含 25 枚棋子");
        }
        Map<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        Set<Position> positions = new HashSet<>();
        for (PiecePlacement placement : placements) {
            if (placement == null || placement.type() == null || placement.position() == null) {
                throw new GameRuleException("INVALID_LAYOUT", "布局包含空数据");
            }
            Position position = placement.position();
            if (!positions.add(position)) throw new GameRuleException("DUPLICATE_POSITION", "同一位置不能放置两枚棋子");
            if (BoardRules.territoryOwner(position) != player) throw new GameRuleException("OUTSIDE_TERRITORY", "棋子超出己方区域");
            if (BoardRules.isCamp(position)) throw new GameRuleException("CAMP_MUST_BE_EMPTY", "行营必须为空");
            if (placement.type() == PieceType.FLAG && !BoardRules.isHeadquarters(position)) {
                throw new GameRuleException("FLAG_REQUIRES_HQ", "军旗必须放在大本营");
            }
            if (placement.type() == PieceType.LANDMINE && !BoardRules.isLastTwoRows(player, position)) {
                throw new GameRuleException("LANDMINE_REQUIRES_BACK_ROWS", "地雷只能放在最后两排");
            }
            if (placement.type() == PieceType.BOMB && BoardRules.isFirstRow(player, position)) {
                throw new GameRuleException("BOMB_FORBIDDEN_ON_FRONT", "炸弹不能放在第一排");
            }
            counts.merge(placement.type(), 1, Integer::sum);
        }
        if (!counts.equals(REQUIRED_COUNTS)) {
            throw new GameRuleException("INVALID_PIECE_SET", "棋子种类或数量不正确");
        }
    }

    public synchronized PublicGameEvent move(int player, String pieceId, Position to) {
        requirePlayer(player);
        if (phase != Phase.PLAYING) throw new GameRuleException("WRONG_PHASE", "对局尚未开始");
        if (player != currentTurn) throw new GameRuleException("NOT_YOUR_TURN", "还没轮到你");
        PieceState attacker = board.values().stream()
                .filter(piece -> piece.id().equals(pieceId))
                .findFirst()
                .orElseThrow(() -> new GameRuleException("PIECE_NOT_FOUND", "棋子不存在"));
        if (attacker.owner() != player) throw new GameRuleException("NOT_YOUR_PIECE", "不能移动其他玩家的棋子");
        if (!BoardRules.legalDestinations(attacker, board).contains(to)) {
            throw new GameRuleException("ILLEGAL_MOVE", "这一步不符合规则");
        }

        PieceState defender = board.get(to);
        board.remove(attacker.position());
        PublicGameEvent event;
        if (defender == null) {
            attacker.moveTo(to);
            board.put(to, attacker);
            event = PublicGameEvent.move(player);
        } else {
            event = resolveBattle(attacker, defender, to);
        }
        revision++;
        if (phase == Phase.PLAYING) advanceTurn();
        return event;
    }

    private PublicGameEvent resolveBattle(PieceState attacker, PieceState defender, Position target) {
        int result = judge(attacker.type(), defender.type());
        board.remove(target);
        if (attacker.type() == PieceType.MARSHAL && result <= 0) revealFlag(attacker.owner());
        if (defender.type() == PieceType.MARSHAL && result >= 0) revealFlag(defender.owner());
        if (result > 0) {
            attacker.moveTo(target);
            board.put(target, attacker);
            if (defender.type() == PieceType.FLAG) eliminate(defender.owner(), "FLAG_LOST");
        } else if (result < 0) {
            board.put(target, defender);
        }
        return PublicGameEvent.clash(attacker.owner(), target);
    }

    private int judge(PieceType attacker, PieceType defender) {
        if (defender == PieceType.FLAG) return 1;
        if (attacker == PieceType.BOMB || defender == PieceType.BOMB) return 0;
        if (defender == PieceType.LANDMINE) return attacker == PieceType.SAPPER ? 1 : -1;
        return Integer.compare(attacker.rank(), defender.rank());
    }

    private void revealFlag(int player) {
        board.values().stream()
                .filter(piece -> piece.owner() == player && piece.type() == PieceType.FLAG)
                .findFirst()
                .ifPresent(PieceState::reveal);
    }

    public synchronized PublicGameEvent surrender(int player) {
        requirePlayer(player);
        if (phase != Phase.PLAYING) throw new GameRuleException("WRONG_PHASE", "对局尚未开始");
        if (!alive[player]) throw new GameRuleException("PLAYER_ELIMINATED", "玩家已经出局");
        eliminate(player, "SURRENDER");
        revision++;
        if (phase == Phase.PLAYING) advanceTurn();
        return PublicGameEvent.eliminated(player, "SURRENDER");
    }

    private void eliminate(int player, String reason) {
        alive[player] = false;
        board.entrySet().removeIf(entry -> entry.getValue().owner() == player);
        boolean northSouth = alive[0] || alive[2];
        boolean eastWest = alive[1] || alive[3];
        if (!northSouth || !eastWest) phase = Phase.FINISHED;
    }

    private void advanceTurn() {
        for (int attempts = 0; attempts < 4; attempts++) {
            currentTurn = (currentTurn + 1) % 4;
            if (alive[currentTurn] && hasLegalMove(currentTurn)) return;
            if (alive[currentTurn]) eliminate(currentTurn, "NO_LEGAL_MOVE");
            if (phase == Phase.FINISHED) return;
        }
    }

    private boolean hasLegalMove(int player) {
        return board.values().stream()
                .filter(piece -> piece.owner() == player)
                .anyMatch(piece -> !BoardRules.legalDestinations(piece, board).isEmpty());
    }

    public synchronized GameSnapshot snapshotFor(int viewer) {
        requirePlayer(viewer);
        List<PieceView> views = new ArrayList<>();
        for (PieceState piece : board.values()) {
            PieceType visibleType = piece.owner() == viewer || piece.revealed() ? piece.type() : null;
            views.add(new PieceView(piece.id(), piece.owner(), piece.position(), visibleType, visibleType != null));
        }
        return new GameSnapshot(phase.name(), viewer, currentTurn, revision, List.copyOf(views));
    }

    public synchronized Phase phase() { return phase; }
    public synchronized long revision() { return revision; }

    private static void requirePlayer(int player) {
        if (player < 0 || player > 3) throw new GameRuleException("INVALID_PLAYER", "玩家编号不正确");
    }
}

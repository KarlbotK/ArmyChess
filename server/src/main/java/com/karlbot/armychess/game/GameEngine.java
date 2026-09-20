package com.karlbot.armychess.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GameEngine {
    public static final long TURN_DURATION_MILLIS = 30_000;
    public static final int MAX_TIMEOUTS = 5;
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
    private final Set<Integer> rematchVotes = new HashSet<>();
    private final boolean[] alive = {true, true, true, true};
    private final int[] timeoutCounts = new int[4];
    private Phase phase = Phase.LAYOUT;
    private int currentTurn;
    private long revision = 1;
    private Long turnDeadlineEpochMs;
    private String winnerTeam;

    public static GameEngine restore(GameEngineState state) {
        if (state == null || state.schemaVersion() != GameEngineState.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported game state");
        }
        if (state.phase() == null || state.alivePlayers() == null || state.alivePlayers().size() != 4
                || state.timeoutCounts() == null || state.timeoutCounts().size() != 4) {
            throw new IllegalArgumentException("invalid game state");
        }

        GameEngine engine = new GameEngine();
        engine.phase = state.phase();
        engine.currentTurn = state.currentTurn();
        requirePlayer(engine.currentTurn);
        engine.revision = Math.max(1, state.revision());
        engine.turnDeadlineEpochMs = state.turnDeadlineEpochMs();
        engine.winnerTeam = state.winnerTeam();
        for (GameEngineState.PersistedPiece piece : state.pieces()) {
            PieceState restored = new PieceState(
                    piece.id(), piece.type(), piece.owner(), piece.position(), piece.revealed());
            if (engine.board.put(restored.position(), restored) != null) {
                throw new IllegalArgumentException("duplicate persisted position");
            }
        }
        engine.submitted.addAll(state.submittedPlayers());
        engine.rematchVotes.addAll(state.rematchVotes());
        for (int player = 0; player < 4; player++) {
            engine.alive[player] = state.alivePlayers().get(player);
            engine.timeoutCounts[player] = state.timeoutCounts().get(player);
        }
        return engine;
    }

    public synchronized GameEngineState snapshotState() {
        List<GameEngineState.PersistedPiece> pieces = board.values().stream()
                .map(piece -> new GameEngineState.PersistedPiece(
                        piece.id(), piece.type(), piece.owner(), piece.position(), piece.revealed()))
                .toList();
        return new GameEngineState(
                GameEngineState.CURRENT_SCHEMA_VERSION,
                phase,
                currentTurn,
                revision,
                turnDeadlineEpochMs,
                winnerTeam,
                pieces,
                submitted.stream().sorted().toList(),
                List.of(alive[0], alive[1], alive[2], alive[3]),
                List.of(timeoutCounts[0], timeoutCounts[1], timeoutCounts[2], timeoutCounts[3]),
                rematchVotes.stream().sorted().toList());
    }

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
        if (submitted.size() == 4) {
            phase = Phase.PLAYING;
            currentTurn = 0;
            resetTurnDeadline(System.currentTimeMillis());
        }
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

    public synchronized List<PublicGameEvent> move(int player, String pieceId, Position to) {
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

        List<Position> path = BoardRules.movePath(attacker, to, board);
        PieceState defender = board.get(to);
        board.remove(attacker.position());
        List<PublicGameEvent> events = new ArrayList<>();
        if (defender == null) {
            attacker.moveTo(to);
            board.put(to, attacker);
            events.add(PublicGameEvent.move(player, path));
        } else {
            events.addAll(resolveBattle(attacker, defender, to, path));
        }
        revision++;
        if (phase == Phase.PLAYING) {
            events.addAll(advanceTurn());
            resetTurnDeadline(System.currentTimeMillis());
        }
        return List.copyOf(events);
    }

    private List<PublicGameEvent> resolveBattle(PieceState attacker, PieceState defender, Position target,
                                                List<Position> path) {
        List<PublicGameEvent> events = new ArrayList<>();
        int result = judge(attacker.type(), defender.type());
        board.remove(target);
        if (attacker.type() == PieceType.MARSHAL && result <= 0) revealFlag(attacker.owner());
        if (defender.type() == PieceType.MARSHAL && result >= 0) revealFlag(defender.owner());
        if (result > 0) {
            attacker.moveTo(target);
            board.put(target, attacker);
            if (defender.type() == PieceType.FLAG) {
                eliminate(defender.owner());
                events.add(PublicGameEvent.eliminated(defender.owner(), "FLAG_LOST"));
            }
        } else if (result < 0) {
            board.put(target, defender);
        }
        events.add(0, PublicGameEvent.clash(attacker.owner(), target, path));
        return List.copyOf(events);
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

    public synchronized List<PublicGameEvent> surrender(int player) {
        requirePlayer(player);
        if (phase != Phase.PLAYING) throw new GameRuleException("WRONG_PHASE", "对局尚未开始");
        if (!alive[player]) throw new GameRuleException("PLAYER_ELIMINATED", "玩家已经出局");
        eliminate(player);
        List<PublicGameEvent> events = new ArrayList<>();
        events.add(PublicGameEvent.eliminated(player, "SURRENDER"));
        revision++;
        if (phase == Phase.PLAYING) {
            events.addAll(advanceTurn());
            resetTurnDeadline(System.currentTimeMillis());
        }
        return List.copyOf(events);
    }

    public synchronized Optional<List<PublicGameEvent>> expireTurn(long nowEpochMs) {
        if (phase != Phase.PLAYING || turnDeadlineEpochMs == null || nowEpochMs < turnDeadlineEpochMs) {
            return Optional.empty();
        }
        int timedOutPlayer = currentTurn;
        timeoutCounts[timedOutPlayer]++;
        List<PublicGameEvent> events = new ArrayList<>();
        if (timeoutCounts[timedOutPlayer] >= MAX_TIMEOUTS) {
            eliminate(timedOutPlayer);
            events.add(PublicGameEvent.eliminated(timedOutPlayer, "TIMEOUT"));
        } else {
            events.add(PublicGameEvent.timedOut(timedOutPlayer, timeoutCounts[timedOutPlayer]));
        }
        revision++;
        if (phase == Phase.PLAYING) events.addAll(advanceTurn());
        resetTurnDeadline(nowEpochMs);
        return Optional.of(List.copyOf(events));
    }

    public synchronized boolean requestRematch(int player) {
        requirePlayer(player);
        if (phase != Phase.FINISHED) throw new GameRuleException("WRONG_PHASE", "当前对局尚未结束");
        if (!rematchVotes.add(player)) return false;
        revision++;
        if (rematchVotes.size() < 4) return false;
        resetForRematch();
        return true;
    }

    private void eliminate(int player) {
        alive[player] = false;
        board.entrySet().removeIf(entry -> entry.getValue().owner() == player);
        boolean northSouth = alive[0] || alive[2];
        boolean eastWest = alive[1] || alive[3];
        if (!northSouth || !eastWest) {
            phase = Phase.FINISHED;
            winnerTeam = northSouth ? "NORTH_SOUTH" : "EAST_WEST";
            turnDeadlineEpochMs = null;
        }
    }

    private List<PublicGameEvent> advanceTurn() {
        List<PublicGameEvent> events = new ArrayList<>();
        for (int attempts = 0; attempts < 4; attempts++) {
            currentTurn = (currentTurn + 1) % 4;
            if (alive[currentTurn] && hasLegalMove(currentTurn)) return List.copyOf(events);
            if (alive[currentTurn]) {
                eliminate(currentTurn);
                events.add(PublicGameEvent.eliminated(currentTurn, "NO_LEGAL_MOVE"));
            }
            if (phase == Phase.FINISHED) return List.copyOf(events);
        }
        return List.copyOf(events);
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
        return new GameSnapshot(
                phase.name(), viewer, currentTurn, revision, List.copyOf(views), turnDeadlineEpochMs,
                List.of(alive[0], alive[1], alive[2], alive[3]),
                List.of(timeoutCounts[0], timeoutCounts[1], timeoutCounts[2], timeoutCounts[3]),
                winnerTeam, rematchVotes.size());
    }

    public synchronized Phase phase() { return phase; }
    public synchronized long revision() { return revision; }

    private void resetTurnDeadline(long nowEpochMs) {
        turnDeadlineEpochMs = phase == Phase.PLAYING ? nowEpochMs + TURN_DURATION_MILLIS : null;
    }

    private void resetForRematch() {
        board.clear();
        submitted.clear();
        rematchVotes.clear();
        for (int player = 0; player < 4; player++) {
            alive[player] = true;
            timeoutCounts[player] = 0;
        }
        phase = Phase.LAYOUT;
        currentTurn = 0;
        winnerTeam = null;
        turnDeadlineEpochMs = null;
        revision++;
    }

    private static void requirePlayer(int player) {
        if (player < 0 || player > 3) throw new GameRuleException("INVALID_PLAYER", "玩家编号不正确");
    }
}

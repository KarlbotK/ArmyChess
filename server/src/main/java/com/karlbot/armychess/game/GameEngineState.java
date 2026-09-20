package com.karlbot.armychess.game;

import java.util.List;

public record GameEngineState(
        int schemaVersion,
        GameEngine.Phase phase,
        int currentTurn,
        long revision,
        Long turnDeadlineEpochMs,
        String winnerTeam,
        List<PersistedPiece> pieces,
        List<Integer> submittedPlayers,
        List<Boolean> alivePlayers,
        List<Integer> timeoutCounts,
        List<Integer> rematchVotes
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record PersistedPiece(
            String id,
            PieceType type,
            int owner,
            Position position,
            boolean revealed
    ) {}
}

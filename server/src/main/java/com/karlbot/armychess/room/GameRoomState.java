package com.karlbot.armychess.room;

import com.karlbot.armychess.game.GameEngineState;

import java.time.Instant;
import java.util.List;

public record GameRoomState(
        int schemaVersion,
        String code,
        Instant createdAt,
        List<PersistedPlayer> players,
        GameEngineState game
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record PersistedPlayer(int playerId, String nickname, String resumeTokenHash) {}
}

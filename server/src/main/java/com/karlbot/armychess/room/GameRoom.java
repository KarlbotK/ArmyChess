package com.karlbot.armychess.room;

import com.karlbot.armychess.game.GameEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GameRoom {
    private final String code;
    private final Instant createdAt;
    private final GameEngine engine;
    private final PlayerSlot[] players = new PlayerSlot[4];

    public GameRoom(String code, PlayerSlot owner) {
        this.code = code;
        this.createdAt = Instant.now();
        this.engine = new GameEngine();
        players[0] = owner;
    }

    public static GameRoom restore(GameRoomState state) {
        if (state == null || state.schemaVersion() != GameRoomState.CURRENT_SCHEMA_VERSION
                || state.code() == null || state.createdAt() == null || state.game() == null) {
            throw new IllegalArgumentException("invalid room state");
        }
        return new GameRoom(state);
    }

    private GameRoom(GameRoomState state) {
        code = state.code();
        createdAt = state.createdAt();
        engine = GameEngine.restore(state.game());
        for (GameRoomState.PersistedPlayer player : state.players()) {
            if (player.playerId() < 0 || player.playerId() >= players.length || players[player.playerId()] != null) {
                throw new IllegalArgumentException("invalid persisted player");
            }
            players[player.playerId()] = PlayerSlot.restore(
                    player.playerId(), player.nickname(), player.resumeTokenHash());
        }
    }

    public String code() { return code; }
    public Instant createdAt() { return createdAt; }
    public GameEngine engine() { return engine; }

    public synchronized PlayerSlot join(String nickname, String token) {
        for (int playerId = 0; playerId < players.length; playerId++) {
            if (players[playerId] == null) {
                PlayerSlot slot = new PlayerSlot(playerId, nickname, token);
                players[playerId] = slot;
                return slot;
            }
        }
        throw new RoomException("ROOM_FULL", "房间已满");
    }

    public synchronized Optional<PlayerSlot> authenticate(String resumeToken) {
        for (PlayerSlot player : players) {
            if (player != null && player.matchesResumeToken(resumeToken)) return Optional.of(player);
        }
        return Optional.empty();
    }

    public synchronized List<PlayerSlot> players() {
        List<PlayerSlot> joined = new ArrayList<>();
        for (PlayerSlot player : players) if (player != null) joined.add(player);
        return List.copyOf(joined);
    }

    public synchronized int playerCount() {
        int count = 0;
        for (PlayerSlot player : players) if (player != null) count++;
        return count;
    }

    public synchronized GameRoomState snapshotState() {
        List<GameRoomState.PersistedPlayer> persistedPlayers = players().stream()
                .map(player -> new GameRoomState.PersistedPlayer(
                        player.playerId(), player.nickname(), player.resumeTokenHash()))
                .toList();
        return new GameRoomState(
                GameRoomState.CURRENT_SCHEMA_VERSION, code, createdAt, persistedPlayers, engine.snapshotState());
    }
}

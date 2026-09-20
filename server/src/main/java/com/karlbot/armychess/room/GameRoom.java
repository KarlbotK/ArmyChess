package com.karlbot.armychess.room;

import com.karlbot.armychess.game.GameEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GameRoom {
    private final String code;
    private final Instant createdAt = Instant.now();
    private final GameEngine engine = new GameEngine();
    private final PlayerSlot[] players = new PlayerSlot[4];

    public GameRoom(String code, PlayerSlot owner) {
        this.code = code;
        players[0] = owner;
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
            if (player != null && player.resumeToken().equals(resumeToken)) return Optional.of(player);
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
}

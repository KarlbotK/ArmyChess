package com.karlbot.armychess.room;

import java.util.List;

public interface RoomStateStore {
    List<GameRoomState> loadAll();
    void save(GameRoomState state);
}

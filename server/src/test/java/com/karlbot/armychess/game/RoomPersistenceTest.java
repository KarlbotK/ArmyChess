package com.karlbot.armychess.game;

import com.karlbot.armychess.room.FileRoomStateStore;
import com.karlbot.armychess.room.GameRoom;
import com.karlbot.armychess.room.RoomRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPersistenceTest {
    @Test
    void restoresSeatsTokensAndRunningGameWithoutWritingRawTokens(@TempDir Path directory) throws IOException {
        ObjectMapper json = new ObjectMapper();
        RoomRegistry first = new RoomRegistry(new FileRoomStateStore(json, directory.toString()));
        List<RoomRegistry.SessionTicket> tickets = new ArrayList<>();
        tickets.add(first.create("南家"));
        String code = tickets.getFirst().roomCode();
        tickets.add(first.join(code, "西家"));
        tickets.add(first.join(code, "北家"));
        tickets.add(first.join(code, "东家"));

        GameRoom original = first.find(code).orElseThrow();
        for (int player = 0; player < 4; player++) {
            original.engine().submitLayout(player, TestLayouts.valid(player));
        }
        first.persist(original);
        GameSnapshot beforeRestart = original.engine().snapshotFor(0);

        String storedJson = Files.readString(directory.resolve(code + ".json"));
        tickets.forEach(ticket -> assertThat(storedJson).doesNotContain(ticket.resumeToken()));

        RoomRegistry restarted = new RoomRegistry(new FileRoomStateStore(json, directory.toString()));
        GameRoom restored = restarted.find(code).orElseThrow();
        GameSnapshot afterRestart = restored.engine().snapshotFor(0);

        assertThat(restored.playerCount()).isEqualTo(4);
        assertThat(afterRestart.phase()).isEqualTo("PLAYING");
        assertThat(afterRestart.revision()).isEqualTo(beforeRestart.revision());
        assertThat(afterRestart.pieces()).containsExactlyInAnyOrderElementsOf(beforeRestart.pieces());
        tickets.forEach(ticket -> assertThat(restored.authenticate(ticket.resumeToken()))
                .get()
                .extracting(player -> player.playerId())
                .isEqualTo(ticket.playerId()));
    }
}

package com.karlbot.armychess.room;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomRegistryTest {
    @Test
    void createsUnpredictableResumeTokensAndCapsRoomAtFourPlayers() {
        RoomRegistry rooms = new RoomRegistry();
        RoomRegistry.SessionTicket owner = rooms.create("松风");
        Set<String> tokens = new HashSet<>();
        tokens.add(owner.resumeToken());

        for (String name : new String[]{"西家", "北家", "东家"}) {
            RoomRegistry.SessionTicket ticket = rooms.join(owner.roomCode(), name);
            tokens.add(ticket.resumeToken());
        }

        assertThat(owner.roomCode()).matches("\\d{6}");
        assertThat(tokens).hasSize(4).allSatisfy(token -> assertThat(token.length()).isGreaterThanOrEqualTo(40));
        assertThatThrownBy(() -> rooms.join(owner.roomCode(), "第五人"))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("房间已满");
    }
}

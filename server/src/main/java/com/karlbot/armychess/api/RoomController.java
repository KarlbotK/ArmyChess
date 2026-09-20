package com.karlbot.armychess.api;

import com.karlbot.armychess.room.RoomException;
import com.karlbot.armychess.room.RoomRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public final class RoomController {
    private final RoomRegistry rooms;

    public RoomController(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public RoomRegistry.SessionTicket create(@Valid @RequestBody NicknameRequest request) {
        return rooms.create(request.nickname());
    }

    @PostMapping("/{roomCode}/join")
    public RoomRegistry.SessionTicket join(@PathVariable String roomCode,
                                           @Valid @RequestBody NicknameRequest request) {
        return rooms.join(roomCode, request.nickname());
    }

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<Map<String, String>> roomError(RoomException error) {
        HttpStatus status = switch (error.code()) {
            case "ROOM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ROOM_FULL" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("code", error.code(), "message", error.getMessage()));
    }

    public record NicknameRequest(@NotBlank @Size(max = 16) String nickname) {}
}

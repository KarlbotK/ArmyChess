package com.karlbot.armychess.room;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class RoomRegistry {
    private static final int MAX_ROOMS = 10_000;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public SessionTicket create(String rawNickname) {
        if (rooms.size() >= MAX_ROOMS) throw new RoomException("ROOM_LIMIT_REACHED", "房间服务繁忙，请稍后重试");
        String nickname = normalizeNickname(rawNickname);
        while (true) {
            String code = "%06d".formatted(random.nextInt(1_000_000));
            String token = token();
            PlayerSlot owner = new PlayerSlot(0, nickname, token);
            GameRoom room = new GameRoom(code, owner);
            if (rooms.putIfAbsent(code, room) == null) return SessionTicket.from(room, owner);
        }
    }

    public SessionTicket join(String code, String rawNickname) {
        GameRoom room = find(code).orElseThrow(() -> new RoomException("ROOM_NOT_FOUND", "房间不存在或已结束"));
        PlayerSlot player = room.join(normalizeNickname(rawNickname), token());
        return SessionTicket.from(room, player);
    }

    public Optional<GameRoom> find(String code) {
        return Optional.ofNullable(rooms.get(normalizeCode(code)));
    }

    public List<GameRoom> all() {
        return List.copyOf(rooms.values());
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip().replaceAll("[\\p{Cntrl}]", "");
        if (nickname.isBlank() || nickname.length() > 16) {
            throw new RoomException("INVALID_NICKNAME", "昵称需为 1–16 个字符");
        }
        return nickname;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.strip();
    }

    public record SessionTicket(String roomCode, int playerId, String nickname, String resumeToken) {
        static SessionTicket from(GameRoom room, PlayerSlot player) {
            return new SessionTicket(room.code(), player.playerId(), player.nickname(), player.resumeToken());
        }
    }
}

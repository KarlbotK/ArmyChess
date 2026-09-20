package com.karlbot.armychess.network;

import com.karlbot.armychess.game.GameEngine;
import com.karlbot.armychess.game.GameRuleException;
import com.karlbot.armychess.game.PiecePlacement;
import com.karlbot.armychess.game.Position;
import com.karlbot.armychess.game.PublicGameEvent;
import com.karlbot.armychess.room.GameRoom;
import com.karlbot.armychess.room.PlayerSlot;
import com.karlbot.armychess.room.RoomRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public final class GameWebSocketHandler extends TextWebSocketHandler {
    private static final int PROTOCOL_VERSION = 1;
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;
    private final RoomRegistry rooms;
    private final ObjectMapper json;

    public GameWebSocketHandler(RoomRegistry rooms, ObjectMapper json) {
        this.rooms = rooms;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Connection connection = authenticate(session.getUri());
        if (connection == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid session"));
            return;
        }
        session.getAttributes().put("roomCode", connection.room().code());
        session.getAttributes().put("playerId", connection.player().playerId());
        WebSocketSession previous = connection.player().bind(session);
        if (previous != null && previous.isOpen()) previous.close(CloseStatus.NORMAL.withReason("reconnected"));
        send(connection.player().session(), Map.of(
                "v", PROTOCOL_VERSION,
                "type", "SESSION_READY",
                "roomCode", connection.room().code(),
                "playerId", connection.player().playerId(),
                "players", playerSummaries(connection.room())
        ));
        broadcastRoomState(connection.room());
        broadcastSnapshots(connection.room());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > MAX_MESSAGE_BYTES) {
            session.close(new CloseStatus(1009, "message too large"));
            return;
        }
        GameRoom room = roomFor(session);
        int playerId = playerId(session);
        String requestId = null;
        try {
            JsonNode input = json.readTree(message.getPayload());
            requestId = requiredText(input, "requestId", 64);
            if (input.path("v").asInt(-1) != PROTOCOL_VERSION) {
                reject(session, requestId, "UNSUPPORTED_VERSION", "客户端版本不兼容");
                return;
            }
            String type = requiredText(input, "type", 40);
            PlayerSlot player = playerFor(room, playerId);
            if (!type.equals("PING") && !player.acceptRequest(requestId)) {
                acknowledge(session, requestId);
                return;
            }
            switch (type) {
                case "PING" -> send(session, Map.of(
                        "v", PROTOCOL_VERSION, "type", "PONG", "requestId", requestId,
                        "sentAt", input.path("sentAt").asLong(0), "serverAt", System.currentTimeMillis()));
                case "SUBMIT_LAYOUT" -> {
                    List<PiecePlacement> placements = json.treeToValue(input.path("placements"),
                            json.getTypeFactory().constructCollectionType(List.class, PiecePlacement.class));
                    room.engine().submitLayout(playerId, placements);
                    acknowledge(session, requestId);
                    broadcastSnapshots(room);
                }
                case "MOVE_REQUEST" -> {
                    requireRevision(room.engine(), input.path("expectedRevision").asLong(-1));
                    String pieceId = requiredText(input, "pieceId", 80);
                    Position to = json.treeToValue(input.path("to"), Position.class);
                    PublicGameEvent event = room.engine().move(playerId, pieceId, to);
                    acknowledge(session, requestId);
                    broadcast(room, envelope("PUBLIC_EVENT", "event", event));
                    broadcastSnapshots(room);
                }
                case "SURRENDER_REQUEST" -> {
                    PublicGameEvent event = room.engine().surrender(playerId);
                    acknowledge(session, requestId);
                    broadcast(room, envelope("PUBLIC_EVENT", "event", event));
                    broadcastSnapshots(room);
                }
                case "CHAT_SEND" -> {
                    if (!player.acceptChat()) throw new GameRuleException("CHAT_RATE_LIMITED", "消息发送太快，请稍后再试");
                    String text = chatText(input.path("text").asText(""));
                    acknowledge(session, requestId);
                    broadcast(room, Map.of(
                            "v", PROTOCOL_VERSION, "type", "CHAT_MESSAGE", "player", playerId,
                            "text", text, "at", Instant.now()));
                }
                default -> reject(session, requestId, "UNKNOWN_MESSAGE", "无法识别的消息类型");
            }
        } catch (GameRuleException error) {
            reject(session, requestId, error.code(), error.getMessage());
        } catch (JacksonException | IllegalArgumentException error) {
            reject(session, requestId, "INVALID_MESSAGE", "消息格式不正确");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            GameRoom room = roomFor(session);
            room.players().stream()
                    .filter(player -> player.playerId() == playerId(session))
                    .findFirst()
                    .ifPresent(player -> player.unbind(session.getId()));
            broadcastRoomState(room);
        } catch (RuntimeException ignored) {
            // Unauthenticated connections do not own a slot.
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private Connection authenticate(URI uri) {
        if (uri == null) return null;
        var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String code = query.getFirst("roomCode");
        String token = query.getFirst("token");
        if (code == null || token == null) return null;
        return rooms.find(code)
                .flatMap(room -> room.authenticate(token).map(player -> new Connection(room, player)))
                .orElse(null);
    }

    private GameRoom roomFor(WebSocketSession session) {
        Object code = session.getAttributes().get("roomCode");
        if (!(code instanceof String roomCode)) throw new IllegalStateException("unbound room");
        return rooms.find(roomCode).orElseThrow(() -> new IllegalStateException("room expired"));
    }

    private int playerId(WebSocketSession session) {
        Object player = session.getAttributes().get("playerId");
        if (!(player instanceof Integer playerId)) throw new IllegalStateException("unbound player");
        return playerId;
    }

    private void broadcastSnapshots(GameRoom room) {
        for (PlayerSlot player : room.players()) {
            WebSocketSession session = player.session();
            if (session != null && session.isOpen()) {
                sendQuietly(session, envelope("SNAPSHOT", "snapshot", room.engine().snapshotFor(player.playerId())));
            }
        }
    }

    private void broadcastRoomState(GameRoom room) {
        broadcast(room, Map.of(
                "v", PROTOCOL_VERSION,
                "type", "ROOM_STATE",
                "roomCode", room.code(),
                "phase", room.engine().phase().name(),
                "players", playerSummaries(room)
        ));
    }

    private void broadcast(GameRoom room, Object payload) {
        for (PlayerSlot player : room.players()) {
            WebSocketSession session = player.session();
            if (session != null && session.isOpen()) sendQuietly(session, payload);
        }
    }

    private List<Map<String, Object>> playerSummaries(GameRoom room) {
        return room.players().stream()
                .map(player -> Map.<String, Object>of("playerId", player.playerId(), "nickname", player.nickname()))
                .toList();
    }

    private PlayerSlot playerFor(GameRoom room, int playerId) {
        return room.players().stream()
                .filter(player -> player.playerId() == playerId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("player expired"));
    }

    private Map<String, Object> envelope(String type, String key, Object value) {
        return Map.of("v", PROTOCOL_VERSION, "type", type, key, value);
    }

    private void acknowledge(WebSocketSession session, String requestId) throws IOException {
        send(session, Map.of("v", PROTOCOL_VERSION, "type", "ACTION_ACCEPTED", "requestId", requestId));
    }

    private void reject(WebSocketSession session, String requestId, String code, String message) {
        sendQuietly(session, Map.of(
                "v", PROTOCOL_VERSION, "type", "ACTION_REJECTED",
                "requestId", requestId == null ? "unknown" : requestId,
                "code", code, "message", message));
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
    }

    private void sendQuietly(WebSocketSession session, Object payload) {
        try {
            send(session, payload);
        } catch (IOException ignored) {
            // A disconnected peer will recover from the next viewer-scoped snapshot on reconnect.
        }
    }

    private static String requiredText(JsonNode input, String field, int maxLength) {
        String value = input.path(field).asText("").strip();
        if (value.isEmpty() || value.length() > maxLength) throw new IllegalArgumentException("invalid " + field);
        return value;
    }

    private static String chatText(String raw) {
        String text = raw.strip().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        if (text.isBlank() || text.length() > 120) throw new GameRuleException("INVALID_CHAT", "消息需为 1–120 个字符");
        return text;
    }

    private static void requireRevision(GameEngine engine, long expected) {
        if (engine.revision() != expected) throw new GameRuleException("STALE_STATE", "棋局已更新，请根据最新棋盘重试");
    }

    private record Connection(GameRoom room, PlayerSlot player) {}
}

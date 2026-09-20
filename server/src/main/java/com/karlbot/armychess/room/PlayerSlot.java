package com.karlbot.armychess.room;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PlayerSlot {
    private final int playerId;
    private final String nickname;
    private final String resumeToken;
    private final Set<String> recentRequestIds = new LinkedHashSet<>();
    private volatile WebSocketSession session;
    private Instant lastChatAt = Instant.EPOCH;

    public PlayerSlot(int playerId, String nickname, String resumeToken) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.resumeToken = resumeToken;
    }

    public int playerId() { return playerId; }
    public String nickname() { return nickname; }
    public String resumeToken() { return resumeToken; }
    public WebSocketSession session() { return session; }

    public synchronized WebSocketSession bind(WebSocketSession next) {
        WebSocketSession previous = session;
        session = new ConcurrentWebSocketSessionDecorator(next, 5_000, 64 * 1024);
        return previous;
    }

    public synchronized void unbind(String sessionId) {
        if (session != null && session.getId().equals(sessionId)) session = null;
    }

    public synchronized boolean acceptRequest(String requestId) {
        if (!recentRequestIds.add(requestId)) return false;
        if (recentRequestIds.size() > 256) {
            String oldest = recentRequestIds.iterator().next();
            recentRequestIds.remove(oldest);
        }
        return true;
    }

    public synchronized boolean acceptChat() {
        Instant now = Instant.now();
        if (Duration.between(lastChatAt, now).toMillis() < 700) return false;
        lastChatAt = now;
        return true;
    }
}

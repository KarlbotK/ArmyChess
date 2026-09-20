package com.karlbot.armychess.room;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PlayerSlot {
    private final int playerId;
    private final String nickname;
    private final String resumeTokenHash;
    private final Set<String> recentRequestIds = new LinkedHashSet<>();
    private volatile WebSocketSession session;
    private Instant lastChatAt = Instant.EPOCH;

    public PlayerSlot(int playerId, String nickname, String resumeToken) {
        this(playerId, nickname, hashToken(resumeToken), true);
    }

    private PlayerSlot(int playerId, String nickname, String resumeTokenHash, boolean alreadyHashed) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.resumeTokenHash = resumeTokenHash;
    }

    public static PlayerSlot restore(int playerId, String nickname, String resumeTokenHash) {
        if (resumeTokenHash == null || resumeTokenHash.isBlank()) {
            throw new IllegalArgumentException("missing resume token hash");
        }
        return new PlayerSlot(playerId, nickname, resumeTokenHash, true);
    }

    public int playerId() { return playerId; }
    public String nickname() { return nickname; }
    public String resumeTokenHash() { return resumeTokenHash; }
    public boolean matchesResumeToken(String resumeToken) {
        if (resumeToken == null || resumeToken.isBlank()) return false;
        byte[] expected = resumeTokenHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hashToken(resumeToken).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
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

    private static String hashToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("missing resume token");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}

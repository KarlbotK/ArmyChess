package com.karlbot.armychess.game;

import java.time.Instant;
import java.util.List;

/** Public by design: this type must never gain attacker/defender/winner piece identity fields. */
public record PublicGameEvent(
        String type,
        Integer actor,
        Position position,
        String reason,
        Integer timeoutCount,
        List<Position> path,
        Instant at
) {
    public PublicGameEvent {
        path = path == null ? null : List.copyOf(path);
    }

    public static PublicGameEvent move(int actor, List<Position> path) {
        return new PublicGameEvent("MOVE_CONFIRMED", actor, null, null, null, path, Instant.now());
    }

    public static PublicGameEvent clash(int actor, Position position, List<Position> path) {
        return new PublicGameEvent("CLASH_OCCURRED", actor, position, null, null, path, Instant.now());
    }

    public static PublicGameEvent eliminated(int player, String reason) {
        return new PublicGameEvent("PLAYER_ELIMINATED", player, null, reason, null, null, Instant.now());
    }

    public static PublicGameEvent timedOut(int player, int timeoutCount) {
        return new PublicGameEvent("TURN_TIMED_OUT", player, null, "TIMEOUT", timeoutCount, null, Instant.now());
    }
}

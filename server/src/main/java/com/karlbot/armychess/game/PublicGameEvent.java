package com.karlbot.armychess.game;

import java.time.Instant;

/** Public by design: this type must never gain attacker/defender/winner piece identity fields. */
public record PublicGameEvent(
        String type,
        Integer actor,
        Position position,
        String reason,
        Integer timeoutCount,
        Instant at
) {
    public static PublicGameEvent move(int actor) {
        return new PublicGameEvent("MOVE_CONFIRMED", actor, null, null, null, Instant.now());
    }

    public static PublicGameEvent clash(int actor, Position position) {
        return new PublicGameEvent("CLASH_OCCURRED", actor, position, null, null, Instant.now());
    }

    public static PublicGameEvent eliminated(int player, String reason) {
        return new PublicGameEvent("PLAYER_ELIMINATED", player, null, reason, null, Instant.now());
    }

    public static PublicGameEvent timedOut(int player, int timeoutCount) {
        return new PublicGameEvent("TURN_TIMED_OUT", player, null, "TIMEOUT", timeoutCount, Instant.now());
    }
}

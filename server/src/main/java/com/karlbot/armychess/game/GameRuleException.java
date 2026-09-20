package com.karlbot.armychess.game;

public class GameRuleException extends RuntimeException {
    private final String code;

    public GameRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}

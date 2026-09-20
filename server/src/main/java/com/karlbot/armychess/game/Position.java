package com.karlbot.armychess.game;

public record Position(int x, int y) {
    public Position {
        if (x < 0 || x >= BoardRules.SIZE || y < 0 || y >= BoardRules.SIZE) {
            throw new IllegalArgumentException("Position is outside the board");
        }
    }
}

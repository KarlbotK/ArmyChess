package com.karlbot.armychess.game;

public record PieceView(String id, int owner, Position position, PieceType visibleType, boolean revealed) {}

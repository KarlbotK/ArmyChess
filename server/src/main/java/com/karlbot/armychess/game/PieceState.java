package com.karlbot.armychess.game;

import java.util.Objects;

public final class PieceState {
    private final String id;
    private final PieceType type;
    private final int owner;
    private Position position;
    private boolean revealed;

    public PieceState(String id, PieceType type, int owner, Position position) {
        this(id, type, owner, position, false);
    }

    PieceState(String id, PieceType type, int owner, Position position, boolean revealed) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.owner = owner;
        this.position = Objects.requireNonNull(position);
        this.revealed = revealed;
    }

    public String id() { return id; }
    public PieceType type() { return type; }
    public int owner() { return owner; }
    public Position position() { return position; }
    public boolean revealed() { return revealed; }
    public void moveTo(Position next) { this.position = Objects.requireNonNull(next); }
    public void reveal() { this.revealed = true; }
}

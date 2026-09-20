package com.karlbot.armychess.game;

public enum PieceType {
    MARSHAL(40),
    GENERAL(39),
    M_GENERAL(38),
    BRIGADIER(37),
    COLONEL(36),
    MAJOR(35),
    CAPTAIN(34),
    LIEUTENANT(33),
    SAPPER(32),
    BOMB(99),
    LANDMINE(88),
    FLAG(0);

    private final int rank;

    PieceType(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}

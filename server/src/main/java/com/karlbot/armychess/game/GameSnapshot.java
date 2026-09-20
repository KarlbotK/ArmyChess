package com.karlbot.armychess.game;

import java.util.List;

public record GameSnapshot(
        String phase,
        int viewer,
        int currentTurn,
        long revision,
        List<PieceView> pieces
) {}

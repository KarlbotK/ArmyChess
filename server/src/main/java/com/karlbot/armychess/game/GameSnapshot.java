package com.karlbot.armychess.game;

import java.util.List;

public record GameSnapshot(
        String phase,
        int viewer,
        int currentTurn,
        long revision,
        List<PieceView> pieces,
        Long turnDeadlineEpochMs,
        List<Boolean> alive,
        List<Integer> timeoutCounts,
        String winnerTeam,
        int rematchVotes
) {}

package com.eightbit.game.event;

import java.time.LocalDate;

/**
 * Published by the game module when a player finishes a puzzle. The leaderboard module listens
 * and updates Redis asynchronously -- the player never waits on ranking maths. This in-process
 * event is the seam that becomes a Redis Stream / RabbitMQ message in Phase 6 (build doc 4b).
 */
public record PuzzleCompletedEvent(
        long userId,
        String username,
        int batchYear,
        String gameType,
        long puzzleId,
        LocalDate puzzleDate,
        int score,
        boolean solved,
        boolean verified
) {}

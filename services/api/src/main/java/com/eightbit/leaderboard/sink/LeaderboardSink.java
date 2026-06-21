package com.eightbit.leaderboard.sink;

import com.eightbit.game.event.PuzzleCompletedEvent;

/**
 * Where a completed-puzzle event goes. Two implementations, chosen by {@code app.leaderboard.sink}:
 *  - inprocess (default): update Redis sorted sets directly in this JVM (modular monolith).
 *  - stream: XADD to a Redis Stream, consumed by the standalone leaderboard service (Phase 6).
 * Swapping the property is the entire "extract a microservice" change — no code rewrite.
 */
public interface LeaderboardSink {
    void publish(PuzzleCompletedEvent event);
}

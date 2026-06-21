package com.eightbit.leaderboard;

import com.eightbit.game.event.PuzzleCompletedEvent;
import com.eightbit.leaderboard.sink.LeaderboardSink;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The leaderboard module's only inbound coupling to the game module: it listens for
 * {@link PuzzleCompletedEvent}. Runs AFTER_COMMIT and @Async, so the player's request returns
 * "saved!" immediately and the ranking happens behind the scenes. The {@link LeaderboardSink}
 * decides whether that means an in-process Redis write or an XADD to the stream consumed by the
 * extracted leaderboard service (Phase 6).
 */
@Component
public class PuzzleCompletedListener {

    private final LeaderboardSink sink;

    public PuzzleCompletedListener(LeaderboardSink sink) {
        this.sink = sink;
    }

    @Async
    @TransactionalEventListener
    public void on(PuzzleCompletedEvent e) {
        sink.publish(e);
    }
}

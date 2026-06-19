package com.eightbit.leaderboard;

import com.eightbit.game.event.PuzzleCompletedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The leaderboard module's only inbound coupling to the game module: it listens for
 * {@link PuzzleCompletedEvent}. Runs AFTER_COMMIT and @Async, so the player's request returns
 * "saved!" immediately and the ranking happens behind the scenes -- exactly the behaviour that
 * becomes a Redis Stream / RabbitMQ consumer when this module is extracted in Phase 6.
 */
@Component
public class PuzzleCompletedListener {

    private final LeaderboardService leaderboard;

    public PuzzleCompletedListener(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @Async
    @TransactionalEventListener
    public void on(PuzzleCompletedEvent e) {
        leaderboard.record(e.gameType(), e.puzzleDate(), e.userId(), e.batchYear(), e.score());
    }
}

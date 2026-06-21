package com.eightbit.leaderboard.sink;

import com.eightbit.game.event.PuzzleCompletedEvent;
import com.eightbit.leaderboard.LeaderboardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default sink: writes Redis sorted sets in-process. Used when running as a single modular monolith.
 */
@Component
@ConditionalOnProperty(name = "app.leaderboard.sink", havingValue = "inprocess", matchIfMissing = true)
public class InProcessLeaderboardSink implements LeaderboardSink {

    private final LeaderboardService leaderboard;

    public InProcessLeaderboardSink(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @Override
    public void publish(PuzzleCompletedEvent e) {
        leaderboard.record(e.gameType(), e.puzzleDate(), e.userId(), e.batchYear(), e.score(), e.verified());
    }
}

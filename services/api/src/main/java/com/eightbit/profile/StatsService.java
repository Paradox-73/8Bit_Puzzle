package com.eightbit.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Owns streak/title bookkeeping. Called synchronously by the game module when an attempt
 * is finalized -- streaks are core to the player's record, so they are written in the same
 * transaction as the attempt (the leaderboard, by contrast, is updated asynchronously).
 */
@Service
public class StatsService {

    private final UserStatsRepository repo;

    public StatsService(UserStatsRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UserStats recordCompletion(long userId, LocalDate puzzleDate, boolean solved,
                                      boolean firstSolver, int solveHourLocal) {
        UserStats s = repo.findById(userId).orElseGet(() -> repo.save(new UserStats(userId)));

        s.setTotalPlayed(s.getTotalPlayed() + 1);

        if (solved) {
            s.setTotalSolved(s.getTotalSolved() + 1);
            LocalDate last = s.getLastSolvedDate();
            if (puzzleDate.equals(last)) {
                // already counted (defensive; UNIQUE attempt should prevent re-entry)
            } else if (last != null && puzzleDate.equals(last.plusDays(1))) {
                s.setCurrentStreak(s.getCurrentStreak() + 1);
            } else {
                s.setCurrentStreak(1);
            }
            s.setLastSolvedDate(puzzleDate);
            s.setBestStreak(Math.max(s.getBestStreak(), s.getCurrentStreak()));

            if (firstSolver) addTitle(s, "First Blood");
            if (solveHourLocal >= 0 && solveHourLocal < 5) addTitle(s, "Library Ghost");
            if (s.getCurrentStreak() >= 7) addTitle(s, "Streak Keeper");
        }

        return repo.save(s);
    }

    private void addTitle(UserStats s, String title) {
        if (!s.getTitles().contains(title)) {
            s.getTitles().add(title);
        }
    }
}

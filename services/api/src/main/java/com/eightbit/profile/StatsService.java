package com.eightbit.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns streak/title bookkeeping. Called synchronously by the game module when an attempt
 * is finalized -- streaks are core to the player's record, so they are written in the same
 * transaction as the attempt (the leaderboard, by contrast, is updated asynchronously).
 */
@Service
public class StatsService {

    /** Solving in <=2 guesses on this many consecutive days trips the leaked-answer warning. */
    public static final int QUICK_SOLVE_FLAG_THRESHOLD = 2;
    private static final int QUICK_SOLVE_GUESSES = 2;

    private final UserStatsRepository repo;

    public StatsService(UserStatsRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public UserStats recordCompletion(long userId, LocalDate puzzleDate, boolean solved,
                                      boolean firstSolver, int solveHourLocal,
                                      int guessesUsed, String gameType) {
        UserStats s = repo.findById(userId).orElseGet(() -> repo.save(new UserStats(userId)));
        boolean wordle = "wordle".equals(gameType);

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

            if (wordle) recordGuessDistribution(s, guessesUsed);
        }

        // Anti-cheat: a leaked answer is typically typed in 1–2 guesses. Track consecutive days of
        // that pattern (Wordle only) and flag once it repeats, so the player is warned and the team
        // is notified by the game layer.
        if (wordle) {
            if (solved && guessesUsed <= QUICK_SOLVE_GUESSES) {
                s.setConsecutiveQuickSolves(s.getConsecutiveQuickSolves() + 1);
                if (s.getConsecutiveQuickSolves() >= QUICK_SOLVE_FLAG_THRESHOLD) {
                    s.setFlagged(true);
                    s.setFlagReason("Solved in ≤" + QUICK_SOLVE_GUESSES + " guesses on "
                            + s.getConsecutiveQuickSolves() + " consecutive days");
                }
            } else {
                s.setConsecutiveQuickSolves(0);
            }
        }

        return repo.save(s);
    }

    private void recordGuessDistribution(UserStats s, int guessesUsed) {
        List<Integer> dist = s.getGuessDistribution();
        if (dist == null || dist.size() < 6) {
            dist = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0));
        }
        int idx = Math.max(1, Math.min(6, guessesUsed)) - 1;
        dist.set(idx, dist.get(idx) + 1);
        s.setGuessDistribution(dist);
    }

    private void addTitle(UserStats s, String title) {
        if (!s.getTitles().contains(title)) {
            s.getTitles().add(title);
        }
    }
}

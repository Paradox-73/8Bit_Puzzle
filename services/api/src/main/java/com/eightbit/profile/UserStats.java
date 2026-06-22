package com.eightbit.profile;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaks and titles live in Postgres (not only Redis) so a cache wipe never erases
 * someone's 60-day streak -- the one bug that makes people quit forever (build doc section 9).
 */
@Entity
@Table(name = "user_stats")
public class UserStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak = 0;

    @Column(name = "total_played", nullable = false)
    private int totalPlayed = 0;

    @Column(name = "total_solved", nullable = false)
    private int totalSolved = 0;

    @Column(name = "last_solved_date")
    private LocalDate lastSolvedDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> titles = new ArrayList<>();

    /** Wordle win distribution: 6 buckets, index 0 = solved in 1 guess … index 5 = solved in 6. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "guess_distribution", columnDefinition = "jsonb")
    private List<Integer> guessDistribution = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0));

    /** Consecutive days solved in <=2 guesses — a leaked-answer tell (build doc anti-cheat). */
    // columnDefinition default lets ddl-auto add the column to a table that already has rows.
    @Column(name = "consecutive_quick_solves", nullable = false, columnDefinition = "integer default 0")
    private int consecutiveQuickSolves = 0;

    /** Set when the quick-solve pattern trips; surfaced as a warning to the player. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean flagged = false;

    @Column(name = "flag_reason", length = 200)
    private String flagReason;

    protected UserStats() {}

    public UserStats(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int v) { this.currentStreak = v; }
    public int getBestStreak() { return bestStreak; }
    public void setBestStreak(int v) { this.bestStreak = v; }
    public int getTotalPlayed() { return totalPlayed; }
    public void setTotalPlayed(int v) { this.totalPlayed = v; }
    public int getTotalSolved() { return totalSolved; }
    public void setTotalSolved(int v) { this.totalSolved = v; }
    public LocalDate getLastSolvedDate() { return lastSolvedDate; }
    public void setLastSolvedDate(LocalDate d) { this.lastSolvedDate = d; }
    public List<String> getTitles() { return titles; }
    public void setTitles(List<String> titles) { this.titles = titles; }
    public List<Integer> getGuessDistribution() { return guessDistribution; }
    public void setGuessDistribution(List<Integer> v) { this.guessDistribution = v; }
    public int getConsecutiveQuickSolves() { return consecutiveQuickSolves; }
    public void setConsecutiveQuickSolves(int v) { this.consecutiveQuickSolves = v; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }

    public int winRatePercent() {
        return totalPlayed == 0 ? 0 : Math.round((totalSolved * 100f) / totalPlayed);
    }
}

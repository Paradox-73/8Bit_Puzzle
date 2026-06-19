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

    public int winRatePercent() {
        return totalPlayed == 0 ? 0 : Math.round((totalSolved * 100f) / totalPlayed);
    }
}

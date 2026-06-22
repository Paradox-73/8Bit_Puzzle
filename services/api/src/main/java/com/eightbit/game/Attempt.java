package com.eightbit.game;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "attempts", uniqueConstraints = {
        // hard stop on replaying a puzzle for a better score
        @UniqueConstraint(name = "uq_attempt_user_puzzle", columnNames = {"user_id", "puzzle_id"})
}, indexes = {
        @Index(name = "idx_attempt_puzzle", columnList = "puzzle_id")
})
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "puzzle_id", nullable = false)
    private Long puzzleId;

    /** The raw guessed words, in order. Colours are recomputed server-side from the answer. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> guesses = new ArrayList<>();

    private Boolean solved;

    @Column
    private Integer score;

    @Column(name = "completion_ms")
    private Integer completionMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eggs_found", columnDefinition = "jsonb")
    private List<String> eggsFound = new ArrayList<>();

    /**
     * Revealed hints. Wordle: [{"kind":"vowel","letter":"A"}] — only that the word contains the
     * letter, never its position. Cryptic: [{"kind":"definition","text":"…"}]. Connections:
     * [{"level":0,"word":"WIFI"}]. Server-authoritative and capped so a hint never solves the puzzle.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> hints = new ArrayList<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Anti-cheat: set when a finished attempt looks suspicious (e.g. solved impossibly fast). */
    @Column(nullable = false)
    private boolean flagged = false;

    @Column(name = "flag_reason", length = 200)
    private String flagReason;

    protected Attempt() {}

    public Attempt(Long userId, Long puzzleId) {
        this.userId = userId;
        this.puzzleId = puzzleId;
    }

    public boolean isFinished() { return finishedAt != null; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPuzzleId() { return puzzleId; }
    public List<String> getGuesses() { return guesses; }
    public void setGuesses(List<String> guesses) { this.guesses = guesses; }
    public Boolean getSolved() { return solved; }
    public void setSolved(Boolean solved) { this.solved = solved; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getCompletionMs() { return completionMs; }
    public void setCompletionMs(Integer completionMs) { this.completionMs = completionMs; }
    public List<String> getEggsFound() { return eggsFound; }
    public void setEggsFound(List<String> eggsFound) { this.eggsFound = eggsFound; }
    public List<Map<String, Object>> getHints() { return hints; }
    public void setHints(List<Map<String, Object>> hints) { this.hints = hints; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
}

package com.eightbit.game;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "puzzles", uniqueConstraints = {
        // one puzzle per game per day
        @UniqueConstraint(name = "uq_puzzle_type_date", columnNames = {"game_type", "publish_date"})
}, indexes = {
        @Index(name = "idx_puzzle_serve", columnList = "game_type, publish_date, status")
})
public class Puzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_type", length = 20, nullable = false)
    private String gameType;

    @Column(name = "publish_date")
    private LocalDate publishDate;          // NULL = evergreen failsafe pool

    @Column
    private Short difficulty;               // 1 (Mon) .. 5 (Fri/weekend)

    /** Per-type content. Wordle: {"answer":"PIXEL"}. NEVER serialized raw to the client. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> content = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "easter_eggs", columnDefinition = "jsonb")
    private Map<String, Object> easterEggs;

    @Column(length = 10, nullable = false)
    private String status = PuzzleStatus.DRAFT;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // --- getters / setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    public Short getDifficulty() { return difficulty; }
    public void setDifficulty(Short difficulty) { this.difficulty = difficulty; }
    public Map<String, Object> getContent() { return content; }
    public void setContent(Map<String, Object> content) { this.content = content; }
    public Map<String, Object> getEasterEggs() { return easterEggs; }
    public void setEasterEggs(Map<String, Object> easterEggs) { this.easterEggs = easterEggs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** The Wordle answer, upper-cased. Stays server-side. */
    public String answer() {
        Object a = content.get("answer");
        return a == null ? null : a.toString().toUpperCase();
    }
}

package com.eightbit.feedback;

import jakarta.persistence.*;

import java.time.Instant;

/** A player-submitted feedback note or bug report. Persisted for the team and emailed on arrival. */
@Entity
@Table(name = "feedback", indexes = {
        @Index(name = "idx_feedback_created", columnList = "created_at")
})
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 30)
    private String username;

    /** "feedback" or "bug". */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 2000)
    private String message;

    /** Optional client context, e.g. the route/page and user agent. */
    @Column(length = 500)
    private String context;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Feedback() {}

    public Feedback(Long userId, String username, String type, String message, String context) {
        this.userId = userId;
        this.username = username;
        this.type = type;
        this.message = message;
        this.context = context;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getContext() { return context; }
    public Instant getCreatedAt() { return createdAt; }
}

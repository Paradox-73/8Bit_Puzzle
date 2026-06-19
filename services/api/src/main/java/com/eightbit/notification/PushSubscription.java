package com.eightbit.notification;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "push_subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_push_endpoint", columnNames = {"endpoint"})
})
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "text", nullable = false)
    private String endpoint;

    @Column(columnDefinition = "text", nullable = false)
    private String p256dh;

    @Column(columnDefinition = "text", nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PushSubscription() {}

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
}

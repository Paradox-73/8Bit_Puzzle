package com.eightbit.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_batch", columnList = "batch_year")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roll_number", unique = true, nullable = false, length = 20)
    private String rollNumber;

    @Column(unique = true, nullable = false, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "batch_year", nullable = false)
    private Integer batchYear;

    @Column(length = 10)
    private String program;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Comma-separated, e.g. "ROLE_USER" or "ROLE_USER,ROLE_EDITOR". */
    @Column(nullable = false, length = 100)
    private String roles = "ROLE_USER";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public List<String> roleList() {
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getBatchYear() { return batchYear; }
    public void setBatchYear(Integer batchYear) { this.batchYear = batchYear; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

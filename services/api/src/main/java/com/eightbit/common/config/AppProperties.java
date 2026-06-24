package com.eightbit.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed view of the {@code app.*} configuration block.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Vapid vapid = new Vapid();
    private final Content content = new Content();
    private final Otp otp = new Otp();
    private final BatchWar batchWar = new BatchWar();
    private final Trial trial = new Trial();

    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }
    public Vapid getVapid() { return vapid; }
    public Content getContent() { return content; }
    public Otp getOtp() { return otp; }
    public BatchWar getBatchWar() { return batchWar; }
    public Trial getTrial() { return trial; }

    public static class Jwt {
        private String secret;
        private long accessTokenTtlMinutes = 720;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTokenTtlMinutes() { return accessTokenTtlMinutes; }
        public void setAccessTokenTtlMinutes(long v) { this.accessTokenTtlMinutes = v; }
    }

    public static class Cors {
        private String allowedOrigins = "*";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String v) { this.allowedOrigins = v; }
    }

    public static class Vapid {
        private String publicKey = "";
        private String privateKey = "";
        private String subject = "mailto:8bit@iiitb.ac.in";

        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String v) { this.publicKey = v; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String v) { this.privateKey = v; }
        public String getSubject() { return subject; }
        public void setSubject(String v) { this.subject = v; }

        public boolean isConfigured() {
            return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
        }
    }

    public static class Content {
        private int bufferWarnBelowDays = 14;
        public int getBufferWarnBelowDays() { return bufferWarnBelowDays; }
        public void setBufferWarnBelowDays(int v) { this.bufferWarnBelowDays = v; }
    }

    public static class Otp {
        /** When false (default), accounts are auto-verified so dev needs no email server. */
        private boolean enabled = false;
        private int ttlMinutes = 10;
        private int length = 6;
        private String emailDomain = "iiitb.ac.in";
        private String fromAddress = "8bit@iiitb.ac.in";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(int v) { this.ttlMinutes = v; }
        public int getLength() { return length; }
        public void setLength(int v) { this.length = v; }
        public String getEmailDomain() { return emailDomain; }
        public void setEmailDomain(String v) { this.emailDomain = v; }
        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String v) { this.fromAddress = v; }
    }

    /**
     * Pre-launch trial / playtest mode. When enabled, players walk every published puzzle
     * back-to-back (no one-per-day gate) so we can gauge solve-rates, timings and difficulty
     * before the real launch. Trial attempts are tagged ({@code attempt.trial=true}), never touch
     * the leaderboard or streaks, and are meant to be purged before go-live (see /admin/trial/reset).
     * {@code endDate} is a hard safety stop: even if {@code enabled} stays true, the trial goes
     * dormant the day after it, so it can never bleed into the real games.
     */
    public static class Trial {
        private boolean enabled = false;
        private java.time.LocalDate endDate = java.time.LocalDate.parse("2026-07-10");
        /** Path to the under-review puzzle file the trial serves. Re-read live when it changes,
         *  so edits to the JSON show up in the app without a redeploy or any hardcoding. */
        private String puzzlesFile = "puzzles-review.json";
        /** Where trial stats snapshots are appended (so they survive the pre-launch data purge). */
        private String statsFile = "trial-stats.json";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public java.time.LocalDate getEndDate() { return endDate; }
        public void setEndDate(java.time.LocalDate v) { this.endDate = v; }
        public String getPuzzlesFile() { return puzzlesFile; }
        public void setPuzzlesFile(String v) { this.puzzlesFile = v; }
        public String getStatsFile() { return statsFile; }
        public void setStatsFile(String v) { this.statsFile = v; }
    }

    /** Cohort sizes for Batch War participation %, keyed by program + batch year. */
    public static class BatchWar {
        private java.util.List<Cohort> cohorts = new java.util.ArrayList<>();
        public java.util.List<Cohort> getCohorts() { return cohorts; }
        public void setCohorts(java.util.List<Cohort> c) { this.cohorts = c; }

        public static class Cohort {
            private String program;
            private int year;
            private int capacity;
            public String getProgram() { return program; }
            public void setProgram(String v) { this.program = v; }
            public int getYear() { return year; }
            public void setYear(int v) { this.year = v; }
            public int getCapacity() { return capacity; }
            public void setCapacity(int v) { this.capacity = v; }
        }
    }
}

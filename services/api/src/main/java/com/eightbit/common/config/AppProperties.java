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

    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }
    public Vapid getVapid() { return vapid; }
    public Content getContent() { return content; }

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
}

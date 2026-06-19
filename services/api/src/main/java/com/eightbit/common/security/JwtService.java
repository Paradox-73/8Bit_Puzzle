package com.eightbit.common.security;

import com.eightbit.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies stateless HS256 access tokens. The token carries the user id (subject),
 * the public username, batch year, and granted roles.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // No real secret configured: mint an EPHEMERAL one so dev still works, but nothing
            // sensitive lives in the repo. Tokens are invalidated on restart. Production MUST set
            // a stable JWT_SECRET (>= 32 bytes).
            this.key = Jwts.SIG.HS256.key().build();
            log.warn("JWT_SECRET not set (or < 32 bytes) -> using an EPHEMERAL signing key. "
                    + "Tokens will not survive a restart. Set JWT_SECRET in production.");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.ttlMinutes = props.getJwt().getAccessTokenTtlMinutes();
    }

    public String issue(long userId, String username, Integer batchYear, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("batchYear", batchYear)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

package com.eightbit.lb;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Read-only JWT parsing using the SAME secret as the game service, so this service can identify the
 * caller for "your rank" and "my batch" scope. Tokens it can't verify are simply treated as
 * anonymous (campus leaderboards stay public).
 */
@Component
public class JwtReader {

    public record Caller(long userId, int batchYear) {
        static final Caller ANON = new Caller(-1, 0);
    }

    private final SecretKey key;

    public JwtReader(@Value("${app.jwt.secret:}") String secret) {
        this.key = (secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= 32)
                ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)) : null;
    }

    public Caller read(String authorizationHeader) {
        if (key == null || authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Caller.ANON;
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorizationHeader.substring(7)).getPayload();
            long id = Long.parseLong(c.getSubject());
            Object by = c.get("batchYear");
            int batch = (by instanceof Number n) ? n.intValue() : 0;
            return new Caller(id, batch);
        } catch (Exception e) {
            return Caller.ANON;
        }
    }
}

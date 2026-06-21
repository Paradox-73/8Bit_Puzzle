package com.eightbit.auth.otp;

import com.eightbit.auth.User;
import com.eightbit.common.config.AppProperties;
import com.eightbit.common.web.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Email OTP: generate a short code, stash it in Redis with a TTL, email it, and verify it. This is
 * the main defence against one person making 50 fake accounts to pump their batch (build doc 6).
 */
@Service
public class VerificationService {

    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();

    private final StringRedisTemplate redis;
    private final EmailSender email;
    private final AppProperties props;

    public VerificationService(StringRedisTemplate redis, EmailSender email, AppProperties props) {
        this.redis = redis;
        this.email = email;
        this.props = props;
    }

    private String codeKey(long userId) { return "otp:" + userId; }
    private String attemptsKey(long userId) { return "otp:attempts:" + userId; }

    /** Generate, store, and email a fresh code to the user. */
    public void issue(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) return;
        String code = generateCode();
        Duration ttl = Duration.ofMinutes(props.getOtp().getTtlMinutes());
        redis.opsForValue().set(codeKey(user.getId()), code, ttl);
        redis.delete(attemptsKey(user.getId()));
        email.send(user.getEmail(), "Your 8Bit verification code",
                "Your 8Bit verification code is " + code + ". It expires in "
                        + props.getOtp().getTtlMinutes() + " minutes.");
    }

    /** Throws ApiException on a bad/expired code; returns normally when the code matches. */
    public void check(long userId, String code) {
        String stored = redis.opsForValue().get(codeKey(userId));
        if (stored == null) {
            throw ApiException.badRequest("OTP_EXPIRED", "Your code expired — request a new one");
        }
        Long attempts = redis.opsForValue().increment(attemptsKey(userId));
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(codeKey(userId));
            throw ApiException.badRequest("OTP_EXPIRED", "Too many attempts — request a new code");
        }
        if (code == null || !stored.equals(code.trim())) {
            throw ApiException.badRequest("BAD_OTP", "That code is incorrect");
        }
        redis.delete(codeKey(userId));
        redis.delete(attemptsKey(userId));
    }

    private String generateCode() {
        int len = Math.max(4, props.getOtp().getLength());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}

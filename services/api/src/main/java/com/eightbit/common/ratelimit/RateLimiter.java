package com.eightbit.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis (build doc anti-cheat checklist, section 14).
 * Cheap: one INCR + a conditional EXPIRE per call. Used to stop scripts hammering the guess endpoint.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true if the action is allowed; false if the limit for this window is exceeded. */
    public boolean allow(String bucket, int limit, Duration window) {
        long windowMs = Math.max(1, window.toMillis());
        long slot = System.currentTimeMillis() / windowMs;
        String key = "rl:" + bucket + ":" + slot;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count != null && count <= limit;
    }
}

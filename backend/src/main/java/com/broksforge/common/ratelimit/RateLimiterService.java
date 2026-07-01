package com.broksforge.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * A distributed fixed-window rate limiter backed by Redis (so limits hold across
 * horizontally-scaled API replicas). Each call atomically increments a per-key
 * counter and sets the window TTL on first use.
 *
 * <p><b>Fail-open:</b> if Redis is unavailable the limiter allows the request rather
 * than blocking authentication on a cache outage — availability is preferred over
 * strict enforcement for this control, and the failure is logged.</p>
 */
@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return {@code true} if the request is within the limit (allowed), {@code false}
     * if the limit for {@code key} has been exceeded within the current window.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
            return count == null || count <= limit;
        } catch (RuntimeException e) {
            // Redis down / unreachable — fail open so auth still works.
            log.warn("Rate limiter unavailable; failing open ({})", e.getMessage());
            return true;
        }
    }
}

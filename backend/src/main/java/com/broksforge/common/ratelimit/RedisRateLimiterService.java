package com.broksforge.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * A distributed fixed-window rate limiter backed by Redis (so limits hold across
 * horizontally-scaled API replicas). Each call atomically increments a per-key
 * counter and sets the window TTL on first use.
 *
 * <p>Only registered when a {@link RedisConnectionFactory} bean exists — an operator who
 * deliberately excludes Redis autoconfiguration (no Redis provisioned) instead gets
 * {@link NoOpRateLimiterService}. When Redis IS configured but transiently unreachable,
 * this fails open (logs a warning, allows the request) rather than blocking authentication
 * on a cache outage.</p>
 */
@Slf4j
@Service
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisRateLimiterService implements RateLimiterService {

    private final StringRedisTemplate redis;

    public RedisRateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
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

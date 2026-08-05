package com.broksforge.common.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * A distributed fixed-window rate limiter backed by Redis (so limits hold across
 * horizontally-scaled API replicas). Each call atomically increments a per-key
 * counter and sets the window TTL on first use.
 *
 * <p>Redis is resolved through an {@link ObjectProvider} rather than by a bean condition.
 * {@code @ConditionalOnBean}/{@code @ConditionalOnMissingBean} are only meaningful on
 * auto-configuration {@code @Bean} methods: on a component-scanned {@code @Service} they are
 * evaluated during scanning, before auto-configuration has registered Spring Data Redis. That
 * ordering made the no-op fallback win even when Redis was configured and healthy, which silently
 * disabled auth rate limiting in every deployment. Deciding at construction time instead cannot be
 * mis-ordered.</p>
 *
 * <p>Where Redis is genuinely absent (an operator who provisioned none) this degrades to allowing
 * every request, and says so at startup. Where Redis is configured but transiently unreachable it
 * fails open per call rather than blocking authentication on a cache outage.</p>
 */
@Slf4j
@Service
public class RedisRateLimiterService implements RateLimiterService {

    private final StringRedisTemplate redis;

    public RedisRateLimiterService(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis.getIfAvailable();
    }

    @PostConstruct
    void reportBacking() {
        if (redis == null) {
            log.warn("Redis is not configured — auth rate limiting is disabled (fail-open no-op). "
                    + "See docs/DEPLOYMENT.md for how to enable it.");
        } else {
            log.info("Auth rate limiting is enforced through Redis.");
        }
    }

    /** True when a real Redis-backed limiter is in effect; false when the no-op fallback is. */
    public boolean enforcing() {
        return redis != null;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (redis == null) {
            return true;
        }
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

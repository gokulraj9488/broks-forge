package com.broksforge.common.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fallback used when no {@link RedisConnectionFactory} bean exists (Redis autoconfiguration
 * deliberately excluded via {@code spring.autoconfigure.exclude} — see docs/DEPLOYMENT.md).
 * Auth-endpoint rate limiting becomes a no-op in that configuration; every other feature is
 * unaffected since nothing else in this codebase depends on Redis.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(RedisConnectionFactory.class)
public class NoOpRateLimiterService implements RateLimiterService {

    @PostConstruct
    void warnOnce() {
        log.warn("Redis is not configured — auth rate limiting is disabled (fail-open no-op). "
                + "See docs/DEPLOYMENT.md for how to re-enable it.");
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        return true;
    }
}

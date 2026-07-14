package com.broksforge.common.ratelimit;

import java.time.Duration;

/**
 * A fixed-window rate limiter keyed by an arbitrary string (typically
 * {@code "rl:auth:<path>:<clientIp>"}). Implementations may be distributed (Redis-backed,
 * holds across replicas) or a local no-op fallback when Redis isn't provisioned — see
 * {@link RedisRateLimiterService} and {@link NoOpRateLimiterService}.
 */
public interface RateLimiterService {

    /**
     * @return {@code true} if the request is within the limit (allowed), {@code false}
     * if the limit for {@code key} has been exceeded within the current window.
     */
    boolean tryAcquire(String key, int limit, Duration window);
}

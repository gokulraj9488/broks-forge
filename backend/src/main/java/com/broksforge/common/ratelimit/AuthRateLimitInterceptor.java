package com.broksforge.common.ratelimit;

import com.broksforge.common.exception.ApiError;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.web.RequestUtils;
import com.broksforge.config.properties.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Applies the {@link RateLimiterService} to sensitive authentication requests,
 * keyed by client IP. On breach it writes the canonical {@link ApiError} envelope
 * with {@link ErrorCode#RATE_LIMITED} (HTTP 429) directly, so the response contract
 * matches the global exception handler exactly. It never touches authentication
 * business logic — it is a pre-handler guard registered by {@code WebMvcConfig}.
 */
@Slf4j
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitInterceptor(RateLimiterService rateLimiterService,
                                    RateLimitProperties properties,
                                    ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!properties.enabled()) {
            return true;
        }
        String clientIp = RequestUtils.clientIp(request);
        String key = "rl:auth:" + request.getRequestURI() + ":" + clientIp;
        boolean allowed = rateLimiterService.tryAcquire(
                key, properties.limit(), Duration.ofSeconds(properties.windowSeconds()));
        if (allowed) {
            return true;
        }
        log.warn("Audit: rate limit exceeded for {} from {}", request.getRequestURI(), clientIp);
        writeTooManyRequests(request, response);
        return false;
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.RATE_LIMITED;
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(code.getHttpStatus().value())
                .error(code.getHttpStatus().getReasonPhrase())
                .code(code.name())
                .message("Too many requests. Please wait a moment and try again.")
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}

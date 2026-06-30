package com.broksforge.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * Small helpers for extracting client metadata from a request.
 */
public final class RequestUtils {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private RequestUtils() {
    }

    /**
     * Resolves the client IP, honouring a single {@code X-Forwarded-For} hop
     * (the value set by the reverse proxy in front of the API).
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}

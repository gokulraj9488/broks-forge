package com.broksforge.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes a correlation ID (stable across a logical operation / client trace)
 * and a request ID (unique per HTTP request) for every request, exposing both via
 * SLF4J {@link MDC} and response headers.
 *
 * <p>This is the seam for distributed tracing: when OpenTelemetry is adopted, the
 * same identifiers map onto trace/span context without touching business code.
 * Inbound values are sanitised to prevent log injection.</p>
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String correlationId = sanitizeOrGenerate(request.getHeader(CORRELATION_ID_HEADER));
        String requestId = sanitizeOrGenerate(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String sanitizeOrGenerate(String candidate) {
        if (StringUtils.hasText(candidate) && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}

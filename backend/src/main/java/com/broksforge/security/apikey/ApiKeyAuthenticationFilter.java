package com.broksforge.security.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests presenting an {@code X-API-Key} header. Delegates
 * verification to the {@link ApiKeyAuthenticator} port implemented by the API
 * key module.
 */
@Slf4j
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyAuthenticator apiKeyAuthenticator;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticator apiKeyAuthenticator) {
        this.apiKeyAuthenticator = apiKeyAuthenticator;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(rawKey) && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyAuthenticator.authenticate(rawKey).ifPresent(principal -> {
                ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(principal);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated API key '{}' for project {}", principal.name(), principal.projectId());
            });
        }
        filterChain.doFilter(request, response);
    }
}

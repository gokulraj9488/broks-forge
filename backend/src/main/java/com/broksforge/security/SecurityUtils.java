package com.broksforge.security;

import com.broksforge.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Convenience accessors for the currently authenticated principal. Kept static
 * and side-effect free so it can be used from any layer (including JPA
 * auditing) without injecting Spring beans.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return the authenticated user's id, or empty for anonymous / API-key requests
     */
    public static Optional<UUID> getCurrentUserId() {
        return getCurrentUserDetails().map(CustomUserDetails::getId);
    }

    /**
     * @return the authenticated user's id or throws if there is none
     */
    public static UUID requireCurrentUserId() {
        return getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    public static Optional<CustomUserDetails> getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return Optional.of(userDetails);
        }
        return Optional.empty();
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String anonymous && "anonymousUser".equals(anonymous));
    }
}

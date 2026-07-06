package com.broksforge.config;

import com.broksforge.common.ratelimit.AuthRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the auth rate-limit interceptor for the sensitive authentication
 * endpoints. Implementing {@link WebMvcConfigurer} (rather than {@code @EnableWebMvc})
 * augments Spring Boot's MVC auto-configuration without replacing it.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthRateLimitInterceptor authRateLimitInterceptor;

    public WebMvcConfig(AuthRateLimitInterceptor authRateLimitInterceptor) {
        this.authRateLimitInterceptor = authRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-verification",
                        "/api/v1/auth/change-password",
                        "/api/v1/auth/confirm-password-change",
                        "/api/v1/auth/password-change/request",
                        "/api/v1/auth/password-change/verify",
                        "/api/v1/auth/password-change/complete");
    }
}

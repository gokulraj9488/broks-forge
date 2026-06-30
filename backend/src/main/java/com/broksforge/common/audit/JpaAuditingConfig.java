package com.broksforge.common.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so that {@code @CreatedDate},
 * {@code @LastModifiedDate}, {@code @CreatedBy} and {@code @LastModifiedBy}
 * are populated automatically on every aggregate.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "applicationAuditAware")
public class JpaAuditingConfig {
}

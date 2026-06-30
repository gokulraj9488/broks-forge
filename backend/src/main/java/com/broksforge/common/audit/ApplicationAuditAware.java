package com.broksforge.common.audit;

import com.broksforge.security.SecurityUtils;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the identifier of the currently authenticated user to Spring Data
 * JPA auditing so that {@code created_by} / {@code updated_by} are populated
 * automatically.
 *
 * <p>For anonymous actions (e.g. self-registration) no auditor is available
 * and the audit columns are left {@code null}.</p>
 */
@Component("applicationAuditAware")
public class ApplicationAuditAware implements AuditorAware<UUID> {

    @Override
    @NonNull
    public Optional<UUID> getCurrentAuditor() {
        return SecurityUtils.getCurrentUserId();
    }
}

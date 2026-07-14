package com.broksforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarantees the actual root cause of a startup failure is visible, even when the platform's
 * log viewer truncates or a downstream symptom (e.g. an {@code UnsatisfiedDependencyException}
 * on the first {@code @Repository} bean needing {@code entityManagerFactory}) is what Spring
 * happens to report first.
 *
 * <p>This is diagnostics-only: it fires on {@link ApplicationFailedEvent}, which only occurs
 * once the application has already failed to start — it cannot change any successful-startup
 * or runtime behavior. It supplements, rather than replaces, Spring Boot's own failure
 * reporting (the full stack trace is still printed via {@code SpringApplication}'s normal
 * failure-analysis path); this adds a short, hard-to-miss summary immediately above it.</p>
 *
 * <p>Registered via {@link org.springframework.boot.SpringApplication#addListeners} in
 * {@code BroksForgeApplication.main} (not a {@code @Component}), matching
 * {@link StartupDiagnosticsLogger} — a failed context may never finish creating enough beans
 * for a {@code @Component}-based listener to be registered at all.</p>
 */
public class StartupFailureDiagnosticsLogger implements ApplicationListener<ApplicationFailedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(StartupFailureDiagnosticsLogger.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable failure = event.getException();
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(failure);

        List<String> chain = new ArrayList<>();
        Throwable current = failure;
        while (current != null) {
            chain.add(current.getClass().getName() + ": " + redact(current.getMessage()));
            Throwable next = current.getCause();
            // Defend against a (theoretically impossible, but not JVM-guaranteed-impossible)
            // self-referential cause chain rather than looping forever.
            current = (next == current) ? null : next;
        }

        log.error("=================== STARTUP FAILED — ROOT CAUSE ===================");
        log.error("Root cause:   {}: {}", rootCause.getClass().getName(), redact(rootCause.getMessage()));
        log.error("Full exception chain (outermost -> root cause), {} level(s):", chain.size());
        for (int i = 0; i < chain.size(); i++) {
            log.error("  [{}] {}", i, chain.get(i));
        }
        log.error("=====================================================================");
        // Printed distinctly (and first) from the root cause specifically, not just the
        // outermost wrapper Spring's own reporting emphasizes — the full stack trace below
        // still contains every level via getCause(), but this ensures the deepest, most
        // actionable frame is never the part that gets scrolled past or truncated.
        log.error("Root cause stack trace:", rootCause);
    }

    /**
     * Applies the same credential redaction {@link StartupDiagnosticsLogger} uses to the
     * datasource URL, since exception messages (HikariCP connection failures, JDBC driver
     * errors) sometimes echo the JDBC URL verbatim, including any embedded credentials.
     */
    private static String redact(String message) {
        return message == null ? "(no message)" : StartupDiagnosticsLogger.maskCredentials(message);
    }
}

package com.broksforge.platform.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the idempotent Knowledge backfill once at startup — <b>only</b> when
 * {@code broksforge.platform.v2.backfill-on-startup=true}. Off by default, so ordinary startup is
 * unchanged. Best-effort: a backfill failure is logged and never fails application startup (it is a bulk
 * data projection, not a correctness precondition). Being idempotent, it is safe to leave enabled.
 */
@Component
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "backfill-on-startup", havingValue = "true")
public class KnowledgeBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBackfillRunner.class);

    private final KnowledgeBackfillService backfill;

    public KnowledgeBackfillRunner(KnowledgeBackfillService backfill) {
        this.backfill = backfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill.backfillAll();
        } catch (RuntimeException e) {
            log.warn("Forge Knowledge backfill skipped (best-effort): {}", e.toString());
        }
    }
}

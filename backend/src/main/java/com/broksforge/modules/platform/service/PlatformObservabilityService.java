package com.broksforge.modules.platform.service;

import com.broksforge.fxp.PlatformHealth;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.modules.platform.web.dto.PlatformHealthResponse;
import com.broksforge.platform.ForgePlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Read-only observability over the engineering platform. Reuses the already-wired {@link ForgePlatform} seam
 * (public APIs only): {@code validate()} for chain + integrity, and the kernel log for the ledger size. Adds
 * no business logic and performs no writes.
 *
 * <p>The whole bean exists only when {@code broksforge.platform.v2.enabled=true}; when the platform is
 * disabled it — and its controller — are absent, so the feature simply isn't offered (reversible by flag).
 * This is the first member of the platform observability namespace; later phases add graph/lineage reads
 * alongside it without changing this class.
 */
@Service
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class PlatformObservabilityService {

    private final ForgePlatform platform;

    public PlatformObservabilityService(ForgePlatform platform) {
        this.platform = platform;
    }

    /** The integrity snapshot for one organization (verified read of the append-only ledger). */
    public PlatformHealthResponse health(UUID organizationId) {
        OrgId org = platform.identity().toOrgId(organizationId);
        PlatformHealth health = platform.health(org);
        long ledgerSize = platform.kernel().log(org).size();
        return new PlatformHealthResponse(true, health.chainValid(),
                health.integrity().clean(), health.integrity().errorCount(), ledgerSize);
    }
}

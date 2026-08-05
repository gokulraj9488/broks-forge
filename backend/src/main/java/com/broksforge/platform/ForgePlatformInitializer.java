package com.broksforge.platform;

import com.broksforge.config.properties.PlatformProperties;
import com.broksforge.fxp.PlatformHealth;
import com.broksforge.kernel.api.OrgId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies Platform V2 health once at startup and <b>fails startup</b> if it is not healthy.
 *
 * <p>Runs {@code verifyChain} + integrity scan (via the platform facade) against the reserved probe org.
 * A healthy result is logged; an unhealthy result throws, so the application will not come up with a broken
 * platform (fail-fast per the approved P0 decision). This adds no REST endpoint and no user-visible
 * behavior — it is a startup assertion only.
 */
@Component
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class ForgePlatformInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ForgePlatformInitializer.class);

    private final ForgePlatform platform;
    private final PlatformProperties properties;

    public ForgePlatformInitializer(ForgePlatform platform, PlatformProperties properties) {
        this.platform = platform;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        OrgId probeOrg = OrgId.of(UUID.fromString(properties.probeOrgId()));
        PlatformHealth health = platform.health(probeOrg);
        if (!health.healthy()) {
            throw new IllegalStateException("Forge Platform V2 failed startup health check: chainValid="
                    + health.chainValid() + ", integrityClean=" + health.integrity().clean()
                    + ", integrityErrors=" + health.integrity().errorCount());
        }
        log.info("Forge Platform V2 initialized and healthy (dormant): schema={}, probeOrg={}, "
                        + "chainValid={}, integrityClean={}",
                properties.schema(), probeOrg.value(), health.chainValid(), health.integrity().clean());
    }
}

package com.broksforge.modules.platform;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.platform.projection.KnowledgeBackfillService;
import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P7 — verifies the read-only platform observability endpoint exposes real integrity, that the engineering
 * ledger reflects projected artifacts, and that access is membership-gated. The endpoint returns identical,
 * side-effect-free reads over the already-wired platform seam.
 */
class PlatformControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private KnowledgeBackfillService backfill;

    @Test
    void exposesPlatformIntegrityAndLedgerGrowsAfterProjection() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Platform Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Platform Project"));
        String path = "/api/v1/organizations/" + orgId + "/platform/health";

        // Baseline: a member sees a healthy platform (a fresh org has an empty but valid ledger).
        JsonNode before = apiGet(token, path, 200);
        assertTrue(before.get("enabled").asBoolean(), "platform should be enabled");
        assertTrue(before.get("chainValid").asBoolean(), "ledger chain should verify");
        assertTrue(before.get("integrityClean").asBoolean(), "integrity scan should be clean");
        long ledgerBefore = before.get("ledgerSize").asLong();

        // Project a real artifact; the ledger must reflect it (proving the graph holds real data).
        Provider provider = new Provider();
        provider.setOrganizationId(orgId);
        provider.setProjectId(projectId);
        provider.setName("Anthropic Prod");
        provider.setType(LlmProvider.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        providerRepository.save(provider);

        backfill.backfillAll(); // idempotent projection into the kernel

        JsonNode after = apiGet(token, path, 200);
        assertTrue(after.get("chainValid").asBoolean());
        assertTrue(after.get("integrityClean").asBoolean());
        assertTrue(after.get("ledgerSize").asLong() > ledgerBefore,
                "the engineering ledger should grow after projecting a provider");
    }

    @Test
    void forbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Owner Org"));

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, "/api/v1/organizations/" + orgId + "/platform/health", null)
                .andExpect(status().isForbidden());
    }
}

package com.broksforge.platform.read;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.modules.provider.service.ProviderService;
import com.broksforge.modules.provider.web.dto.ProviderResponse;
import com.broksforge.modules.user.repository.UserRepository;
import com.broksforge.platform.projection.KnowledgeBackfillService;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Knowledge-backed read returns the same observable value as V1 (parity), that the wired
 * ProviderService read paths ({@code get} and {@code list}) return the identical name, and that the verified
 * read-through falls back to V1 when Knowledge is stale or absent — so behavior is always identical to V1.
 */
class KnowledgeReadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeReadService knowledgeReads;

    @Autowired
    private KnowledgeReadFacade facade;

    @Autowired
    private KnowledgeBackfillService backfill;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void knowledgeReadMatchesV1AndFallsBackWhenInconsistent() throws Exception {
        // A real org + project so the provider's foreign keys are satisfied.
        String email = uniqueEmail();
        String token = registerAndGetToken(email, "StrongPass!2026");
        UUID actorId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        UUID orgId = UUID.fromString(createOrg(token, "Read Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Read Project"));

        Provider provider = new Provider();
        provider.setOrganizationId(orgId);
        provider.setProjectId(projectId);
        provider.setName("Anthropic Prod");
        provider.setType(LlmProvider.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        Provider saved = providerRepository.save(provider);

        backfill.backfillAll(); // projects the provider into Platform V2 Knowledge (idempotent)

        // Parity: the Knowledge-backed read returns the same value V1 holds.
        assertEquals("Anthropic Prod", knowledgeReads.providerName(orgId, saved.getId()).orElseThrow());

        // Consistent → the verified read-through serves the Knowledge value (identical to V1).
        assertEquals("Anthropic Prod", facade.providerName(orgId, saved.getId(), "Anthropic Prod"));
        assertTrue(facade.isKnowledgeConsistentProviderName(orgId, saved.getId(), "Anthropic Prod"));

        // The wired single-read path returns the identical name.
        assertEquals("Anthropic Prod",
                providerService.get(actorId, orgId, projectId, saved.getId()).name());

        // The wired list path (P5) returns the identical name for the same provider.
        ProviderResponse fromList = providerService
                .list(actorId, orgId, projectId, PageRequest.of(0, 20)).content().stream()
                .filter(p -> p.id().equals(saved.getId()))
                .findFirst().orElseThrow();
        assertEquals("Anthropic Prod", fromList.name());

        // Stale (V1 renamed, not re-projected) → falls back to the current V1 value.
        assertEquals("Anthropic Prod (renamed)",
                facade.providerName(orgId, saved.getId(), "Anthropic Prod (renamed)"));

        // Not projected → falls back to V1.
        assertEquals("whatever", facade.providerName(orgId, UUID.randomUUID(), "whatever"));
    }
}

package com.broksforge.modules.platform;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P9 — verifies the unified registry aggregates real artifacts across types, supports server-side search /
 * type-filter / pagination, exposes type counts, and is membership-gated. Discovery only; no data invented.
 */
class PlatformRegistryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Test
    void aggregatesFiltersAndSearchesRealArtifacts() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Registry Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Registry Project"));

        Provider provider = new Provider();
        provider.setOrganizationId(orgId);
        provider.setProjectId(projectId);
        provider.setName("Anthropic Prod");
        provider.setType(LlmProvider.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        providerRepository.save(provider);

        registerAgent(token, orgId.toString(), projectId.toString(), "Registry Agent");
        createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Greeting");
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Registry Dataset");
        registerAgentAndEval(token, orgId, projectId, datasetId);

        String base = "/api/v1/organizations/" + orgId + "/platform/registry";

        // Types: every core kind is counted and present.
        JsonNode types = apiGet(token, base + "/types", 200);
        Set<String> typeSet = new HashSet<>();
        types.forEach(t -> {
            if (t.get("count").asLong() > 0) typeSet.add(t.get("type").asText());
        });
        assertTrue(typeSet.containsAll(Set.of("project", "provider", "agent", "prompt", "dataset", "evaluation")),
                "expected all core artifact types with counts, got " + typeSet);

        // Default listing is paginated and aggregates across types.
        JsonNode all = apiGet(token, base, 200);
        assertTrue(all.get("totalElements").asLong() >= 6, "registry should aggregate all created artifacts");
        assertTrue(all.get("content").size() > 0);

        // Type filter narrows to a single kind.
        JsonNode prompts = apiGet(token, base + "?type=prompt", 200);
        assertTrue(prompts.get("content").size() >= 1);
        prompts.get("content").forEach(i -> assertEquals("prompt", i.get("type").asText()));

        // Partial-match search finds the prompt by name.
        JsonNode search = apiGet(token, base + "?q=greet", 200);
        boolean found = false;
        for (JsonNode i : search.get("content")) {
            if (i.get("type").asText().equals("prompt") && i.get("name").asText().equals("Greeting")) found = true;
        }
        assertTrue(found, "search q=greet should return the 'Greeting' prompt");
    }

    @Test
    void forbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Owner Registry Org"));

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, "/api/v1/organizations/" + orgId + "/platform/registry", null)
                .andExpect(status().isForbidden());
    }

    private void registerAgentAndEval(String token, UUID orgId, UUID projectId, String datasetId) throws Exception {
        String agentId = registerAgent(token, orgId.toString(), projectId.toString(), "Eval Agent");
        createEvaluationJob(token, orgId.toString(), projectId.toString(), agentId, datasetId, "Registry Eval");
    }
}

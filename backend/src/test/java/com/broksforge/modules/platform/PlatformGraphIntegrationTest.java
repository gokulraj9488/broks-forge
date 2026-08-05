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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8 — verifies the read-only engineering graph endpoint returns real artifacts as nodes and real
 * relationships as edges, and is membership-gated. The graph is assembled from live domain state; nothing is
 * fabricated.
 */
class PlatformGraphIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Test
    void returnsRealArtifactsAndRelationships() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Graph Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Graph Project"));

        // A real engineering graph: provider + agent + prompt + dataset + an evaluation linking agent & dataset.
        Provider provider = new Provider();
        provider.setOrganizationId(orgId);
        provider.setProjectId(projectId);
        provider.setName("Anthropic Prod");
        provider.setType(LlmProvider.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        providerRepository.save(provider);

        String agentId = registerAgent(token, orgId.toString(), projectId.toString(), "Graph Agent");
        createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Graph Prompt");
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Graph Dataset");
        createEvaluationJob(token, orgId.toString(), projectId.toString(), agentId, datasetId, "Graph Eval");

        JsonNode body = apiGet(token, "/api/v1/organizations/" + orgId + "/platform/graph", 200);

        Set<String> nodeTypes = new HashSet<>();
        body.get("nodes").forEach(n -> nodeTypes.add(n.get("type").asText()));
        assertTrue(nodeTypes.containsAll(Set.of(
                        "organization", "project", "provider", "agent", "prompt", "dataset", "evaluation")),
                "graph should contain every core artifact type, got " + nodeTypes);

        // The evaluation must connect to the agent it evaluates and the dataset it uses.
        boolean evaluatesAgent = false;
        boolean usesDataset = false;
        for (JsonNode e : body.get("edges")) {
            String rel = e.get("relation").asText();
            String source = e.get("source").asText();
            String target = e.get("target").asText();
            if (rel.equals("evaluates") && source.startsWith("evaluation:") && target.equals("agent:" + agentId)) {
                evaluatesAgent = true;
            }
            if (rel.equals("uses") && source.startsWith("evaluation:") && target.equals("dataset:" + datasetId)) {
                usesDataset = true;
            }
        }
        assertTrue(evaluatesAgent, "expected an evaluation->agent 'evaluates' edge");
        assertTrue(usesDataset, "expected an evaluation->dataset 'uses' edge");
    }

    @Test
    void forbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Owner Graph Org"));

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, "/api/v1/organizations/" + orgId + "/platform/graph", null)
                .andExpect(status().isForbidden());
    }
}

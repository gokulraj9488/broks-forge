package com.broksforge.modules.platform;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P10 — verifies the engineering-evolution endpoint derives real dependencies, dependents, transitive impact,
 * historical revisions and evidence from the live model, and is membership-gated. Nothing is fabricated.
 */
class PlatformEvolutionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void derivesDependenciesDependentsHistoryAndEvidence() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Evolution Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Evolution Project"));

        String agentId = registerAgent(token, orgId.toString(), projectId.toString(), "Evo Agent");
        String promptId = createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Evo Prompt");
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Evo Dataset");
        String jobId = createEvaluationJob(token, orgId.toString(), projectId.toString(), agentId, datasetId, "Evo Eval");

        String base = "/api/v1/organizations/" + orgId + "/platform/evolution";

        // An evaluation depends on the agent it evaluates and the dataset it uses.
        JsonNode evalEvo = apiGet(token, base + "/evaluation/" + jobId, 200);
        Set<String> depTypes = new HashSet<>();
        evalEvo.get("dependencies").forEach(d -> depTypes.add(d.get("type").asText()));
        assertTrue(depTypes.containsAll(Set.of("agent", "dataset")),
                "evaluation should depend on agent + dataset, got " + depTypes);

        // The agent is depended on by that evaluation, impacted downstream, and has it as evidence.
        JsonNode agentEvo = apiGet(token, base + "/agent/" + agentId, 200);
        boolean dependentEval = false;
        for (JsonNode d : agentEvo.get("dependents")) {
            if (d.get("type").asText().equals("evaluation") && d.get("entityId").asText().equals(jobId)) {
                dependentEval = true;
            }
        }
        assertTrue(dependentEval, "agent should be depended on by the evaluation");
        assertTrue(agentEvo.get("impactCount").asInt() >= 1, "agent impact should include the evaluation");
        assertTrue(agentEvo.get("evidence").size() >= 1, "agent should have the evaluation as evidence");
        assertEquals(jobId, agentEvo.get("evidence").get(0).get("entityId").asText());

        // The prompt has real historical revisions (its versions).
        JsonNode promptEvo = apiGet(token, base + "/prompt/" + promptId, 200);
        assertTrue(promptEvo.get("history").size() >= 1, "prompt should expose its version history");

        // Unknown artifact → 404.
        call("GET", token, base + "/agent/" + UUID.randomUUID(), null).andExpect(status().isNotFound());
    }

    @Test
    void forbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Owner Evolution Org"));

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, "/api/v1/organizations/" + orgId + "/platform/evolution/agent/" + UUID.randomUUID(),
                null).andExpect(status().isForbidden());
    }
}

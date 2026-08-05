package com.broksforge.modules.platform;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P11 — verifies the engineering-intelligence layer derives real knowledge objects (observation, claim,
 * decision, evidence, knowledge), engineering memory, and the "AI Git" revision timeline/comparison entirely
 * from the live model, is fetchable by id, overlays the graph, and is membership-gated. Nothing is fabricated.
 */
class PlatformIntelligenceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void derivesKnowledgeMemoryAndRevisionsFromTheLiveModel() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Intelligence Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Intelligence Project"));
        String base = projectBase(orgId.toString(), projectId.toString());

        String agentId = registerAgent(token, orgId.toString(), projectId.toString(), "Intel Agent");
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Intel Dataset");

        // A prompt with two versions — v1 then v2 (active) — so promotion + supersede is real.
        String promptId = idOf(apiPost(token, base + "/prompts", Map.of("name", "Intel Prompt"), 201));
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Answer {{question}}", "activate", true), 201);
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Answer {{question}} for {{user}} carefully", "activate", true), 201);

        // An evaluation of the agent over the dataset — the source of observation + evidence.
        String jobId = createEvaluationJob(token, orgId.toString(), projectId.toString(), agentId, datasetId, "Intel Eval");

        String platform = "/api/v1/organizations/" + orgId + "/platform";

        // ---- Knowledge catalog: contains derived objects across kinds. ----
        JsonNode catalog = apiGet(token, platform + "/knowledge?size=100", 200);
        Set<String> kinds = new HashSet<>();
        catalog.get("content").forEach(o -> kinds.add(o.get("type").asText()));
        assertTrue(kinds.containsAll(Set.of("observation", "evidence", "decision", "claim", "knowledge")),
                "knowledge catalog should span all knowledge kinds, got " + kinds);

        // Type filter narrows to a single kind.
        JsonNode decisions = apiGet(token, platform + "/knowledge?type=decision&size=100", 200);
        assertTrue(decisions.get("content").size() >= 1);
        decisions.get("content").forEach(o -> assertEquals("decision", o.get("type").asText()));

        // ---- Prompt intelligence: promotion decision, claim, knowledge, memory. ----
        JsonNode promptIntel = apiGet(token, platform + "/intelligence/prompt/" + promptId, 200);
        assertTrue(promptIntel.get("decisions").size() >= 1, "prompt should have a promotion decision");
        assertTrue(promptIntel.get("knowledge").size() >= 1, "prompt should have durable knowledge");
        assertTrue(promptIntel.get("memory").size() >= 1, "prompt should have engineering memory (the why)");
        String decisionId = promptIntel.get("decisions").get(0).get("id").asText();
        assertTrue(promptIntel.get("decisions").get(0).get("summary").asText().contains("superseding"),
                "promotion decision should record that v2 superseded v1");

        // Fetch that decision by id (composite id round-trips).
        JsonNode decision = apiGet(token, platform + "/decision/" + decisionId, 200);
        assertEquals("decision", decision.get("type").asText());
        // Type mismatch on the same id is a 404 (a decision id is not a claim).
        call("GET", token, platform + "/claim/" + decisionId, null).andExpect(status().isNotFound());

        // ---- Agent intelligence: the evaluation is observed and is evidence. ----
        JsonNode agentIntel = apiGet(token, platform + "/intelligence/agent/" + agentId, 200);
        assertTrue(agentIntel.get("evidence").size() >= 1, "agent should have the evaluation as evidence");
        boolean fromEval = false;
        for (JsonNode e : agentIntel.get("evidence")) {
            if (e.get("artifactEntityId").asText().equals(jobId)) fromEval = true;
        }
        assertTrue(fromEval, "agent evidence should trace to the evaluation");

        // ---- AI Git: revision timeline + comparison. ----
        JsonNode timeline = apiGet(token, platform + "/revisions/prompt/" + promptId, 200);
        assertEquals(2, timeline.get("revisions").size(), "prompt has two real revisions");
        assertTrue(timeline.get("promotions").asInt() >= 1);
        String newest = timeline.get("revisions").get(0).get("id").asText();
        String oldest = timeline.get("revisions").get(1).get("id").asText();

        JsonNode diff = apiGet(token,
                platform + "/compare?type=prompt&entityId=" + promptId + "&base=" + oldest + "&target=" + newest, 200);
        boolean templateChanged = false;
        for (JsonNode d : diff.get("diffs")) {
            if (d.get("field").asText().equals("template") && d.get("change").asText().equals("changed")) {
                templateChanged = true;
            }
        }
        assertTrue(templateChanged, "comparing the two prompt revisions should show the template changed");

        // ---- Graph overlay: knowledge nodes appear when requested; base graph is unchanged. ----
        JsonNode overlay = apiGet(token, platform + "/graph?include=knowledge", 200);
        Set<String> overlayTypes = new HashSet<>();
        overlay.get("nodes").forEach(n -> overlayTypes.add(n.get("type").asText()));
        assertTrue(overlayTypes.contains("decision") && overlayTypes.contains("knowledge"),
                "knowledge overlay should add knowledge nodes, got " + overlayTypes);
        assertTrue(overlayTypes.contains("agent") && overlayTypes.contains("prompt"),
                "overlay must still contain the base artifact nodes");

        JsonNode plain = apiGet(token, platform + "/graph", 200);
        plain.get("nodes").forEach(n ->
                assertTrue(!"decision".equals(n.get("type").asText()) && !"knowledge".equals(n.get("type").asText()),
                        "the default graph must not contain knowledge nodes (P8 contract preserved)"));

        // Unknown artifact / id → 404.
        call("GET", token, platform + "/intelligence/prompt/" + UUID.randomUUID(), null)
                .andExpect(status().isNotFound());
        call("GET", token, platform + "/decision/decision:prompt-version:" + UUID.randomUUID(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void forbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Owner Intelligence Org"));

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, "/api/v1/organizations/" + orgId + "/platform/knowledge", null)
                .andExpect(status().isForbidden());
    }
}

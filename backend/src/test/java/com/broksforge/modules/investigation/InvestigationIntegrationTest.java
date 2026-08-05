package com.broksforge.modules.investigation;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P13 — verifies the Root Cause Explorer assembles a real investigation rather than rendering an error.
 *
 * <p>The assertions are about what makes this an investigation workspace instead of an error viewer: the
 * chronology is ordered and contains the engineering events that surround the failure (not just the
 * failure), the causal chain reaches past the immediate cause into contributing, historical and
 * related-change depths, every constitutional question is answered, everything references a record that
 * really exists, and each cause continues into a workflow the platform already has.
 */
class InvestigationIntegrationTest extends AbstractIntegrationTest {

    private static final Set<String> EPISTEMIC = Set.of("derived", "inferred", "suggested", "unknown");
    private static final Set<String> VERDICTS = Set.of("healthy", "attention", "risk", "failed", "unknown");
    private static final Set<String> CONFIDENCE = Set.of("consistent-with", "likely", "near-certain");
    private static final Set<String> LAYERS = Set.of("immediate", "contributing", "historical", "related-change");

    /** Every action must name a surface the platform already has — an investigation invents no destinations. */
    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "openGraph", "openExecutionGraph", "openFailureGraph", "openIntelligence", "openEvolution",
            "openRevisions", "compareRevisions", "openKnowledge", "openRegistry", "openEvaluation",
            "openAnalytics", "openInsights", "startInvestigation");

    private static final List<String> STORY_QUESTIONS = List.of(
            "What happened?", "Why?", "What changed?", "Has this happened before?",
            "Who or what was affected?", "How confident are we?", "What evidence supports this?",
            "What should we do next?");

    @Test
    void assemblesAnInvestigationFromTheEngineeringRecord() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Investigation Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Investigation Project"));
        String base = projectBase(orgId.toString(), projectId.toString());

        // An agent whose endpoint refuses connections produces REAL failed runs without touching the network.
        String agentId = idOf(apiPost(token, base + "/agents", Map.of(
                "name", "Payments Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/agent", "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Payments Data");

        // A promoted prompt gives the investigation an AI Git chain and a related change to reason about.
        String promptId = idOf(apiPost(token, base + "/prompts", Map.of("name", "Payments Prompt"), 201));
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Refund {{order}}", "activate", true, "notes", "Baseline wording."), 201);
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Refund {{order}} for {{user}}", "activate", true,
                        "notes", "Added the user for tone."), 201);

        String firstId = idOf(apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Payments Quality #1", "agentId", agentId, "datasetId", datasetId,
                "promptId", promptId, "autoRun", true), 201));
        String jobId = idOf(apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Payments Quality #2", "agentId", agentId, "datasetId", datasetId,
                "promptId", promptId, "autoRun", true), 201));

        JsonNode investigation = apiGet(token, investigations(orgId) + "/evaluation/" + jobId
                + "?projectId=" + projectId, 200);

        // ---- The subject and the verdict --------------------------------------------------------------
        assertFalse(investigation.get("id").asText().isBlank());
        assertEquals("evaluation:" + jobId, investigation.get("subject").get("id").asText());
        JsonNode verdict = investigation.get("verdict");
        assertTrue(VERDICTS.contains(verdict.get("state").asText()), verdict.get("state").asText());
        assertTrue(EPISTEMIC.contains(verdict.get("status").asText()));
        assertTrue(CONFIDENCE.contains(verdict.get("confidence").asText()));
        assertFalse(verdict.get("headline").asText().isBlank());
        assertFalse(verdict.get("basis").asText().isBlank(), "a verdict must say what it was read from");

        // ---- The engineering timeline: ordered, and about more than the failure ------------------------
        JsonNode timeline = investigation.get("timeline");
        assertTrue(timeline.size() >= 4,
                "the chronology must carry the engineering context around the failure, got " + timeline.size());
        String previous = null;
        Set<String> kinds = new HashSet<>();
        for (JsonNode event : timeline) {
            assertFalse(event.get("title").asText().isBlank());
            assertFalse(event.get("at").asText().isBlank(), "every event must be dated");
            assertTrue(VERDICTS.contains(event.get("state").asText()));
            kinds.add(event.get("kind").asText());
            String at = event.get("at").asText();
            if (previous != null) {
                assertTrue(previous.compareTo(at) <= 0, "the timeline must read oldest-first");
            }
            previous = at;
        }
        assertTrue(kinds.contains("evaluation"), "the run itself must appear on the timeline");
        assertTrue(kinds.contains("promotion") || kinds.contains("revision"),
                "a promoted prompt must appear on the timeline, got kinds " + kinds);
        assertTrue(kinds.contains("run") || kinds.contains("precedent"),
                "the moment it broke, or an earlier one, must appear: " + kinds);

        // ---- The causal chain reaches past the immediate cause -----------------------------------------
        JsonNode causes = investigation.get("causes");
        assertTrue(causes.size() >= 2, "an investigation must not stop at the symptom");
        Set<String> layers = new HashSet<>();
        for (JsonNode cause : causes) {
            String layer = cause.get("layer").asText();
            assertTrue(LAYERS.contains(layer), "unknown causal layer " + layer);
            layers.add(layer);
            assertFalse(cause.get("title").asText().isBlank());
            assertFalse(cause.get("explanation").asText().isBlank(),
                    "a cause must explain why the record supports it");
            assertTrue(EPISTEMIC.contains(cause.get("status").asText()));
            assertTrue(CONFIDENCE.contains(cause.get("confidence").asText()));
            assertTrue(KNOWN_ACTIONS.contains(cause.get("action").get("kind").asText()),
                    "unknown action kind " + cause.get("action").get("kind").asText());
        }
        assertTrue(layers.contains("immediate"), "every investigation names an immediate cause");
        assertTrue(layers.contains("historical"),
                "a second failure on the same ground must produce a historical cause, got " + layers);

        // ---- The precedent is found, and it is the first evaluation ------------------------------------
        JsonNode precedents = investigation.get("references").get("precedents");
        assertTrue(precedents.size() >= 1, "the earlier failure on the same ground must be found");
        boolean citesFirst = false;
        for (JsonNode ref : precedents) {
            citesFirst |= ref.get("id").asText().equals("evaluation:" + firstId);
        }
        assertTrue(citesFirst, "the precedent must be the earlier evaluation on the same ground");

        // ---- The engineering story answers every constitutional question -------------------------------
        JsonNode story = investigation.get("story");
        assertEquals(STORY_QUESTIONS.size(), story.size());
        for (int i = 0; i < STORY_QUESTIONS.size(); i++) {
            assertEquals(STORY_QUESTIONS.get(i), story.get(i).get("question").asText());
            assertFalse(story.get(i).get("answer").asText().isBlank(),
                    STORY_QUESTIONS.get(i) + " must be answered");
            assertTrue(EPISTEMIC.contains(story.get(i).get("status").asText()));
            assertFalse(story.get(i).get("basis").asText().isBlank());
        }
        assertTrue(story.get(3).get("answer").asText().startsWith("Yes"),
                "the second failure on this ground has a precedent: " + story.get(3).get("answer").asText());

        // ---- The chains the workspace renders ----------------------------------------------------------
        JsonNode refs = investigation.get("references");
        assertTrue(refs.get("artifacts").size() >= 3, "agent, prompt and dataset are the ground it ran on");
        assertTrue(refs.get("evidence").size() >= 1, "the evaluation itself is evidence");
        assertTrue(refs.get("revisions").size() >= 2, "the prompt's AI Git chain must be assembled");
        assertTrue(refs.get("relatedEvaluations").size() >= 1, "the sibling evaluation is related");
        assertTrue(investigation.get("memory").size() >= 1,
                "the promoted prompt's recorded reasoning must travel with the investigation");

        // ---- Everything is grounded, and every recommendation opens a real workflow ---------------------
        assertGrounded(investigation);
        assertTrue(investigation.get("recommendations").size() >= 1);
        for (JsonNode rec : investigation.get("recommendations")) {
            assertFalse(rec.get("title").asText().isBlank());
            assertFalse(rec.get("why").asText().isBlank());
            assertTrue(CONFIDENCE.contains(rec.get("confidence").asText()));
            assertTrue(KNOWN_ACTIONS.contains(rec.get("action").get("kind").asText()));
        }

        // ---- It stays a conversation -------------------------------------------------------------------
        assertTrue(investigation.get("followUps").size() >= 3,
                "an investigation must continue into Brok");
        for (JsonNode follow : investigation.get("followUps")) {
            assertFalse(follow.get("question").asText().isBlank());
        }
        assertNotNull(investigation.get("context").get("graphNodeIds"));
        assertTrue(investigation.get("context").get("graphNodeIds").size() >= 1,
                "the graph beside the investigation must know what to focus");
    }

    /** A healthy evaluation is investigated honestly — no root cause is invented for a run that passed. */
    @Test
    void reportsAHealthyEvaluationWithoutInventingACause() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Healthy Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Healthy Project"));
        String base = projectBase(orgId.toString(), projectId.toString());

        String agentId = idOf(apiPost(token, base + "/agents", Map.of(
                "name", "Quiet Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/agent", "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Quiet Data");

        // Created without auto-run: nothing has been measured, so nothing may be diagnosed.
        String jobId = idOf(apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Unmeasured Quality", "agentId", agentId, "datasetId", datasetId), 201));

        JsonNode investigation = apiGet(token, investigations(orgId) + "/evaluation/" + jobId, 200);
        assertGrounded(investigation);
        assertEquals(STORY_QUESTIONS.size(), investigation.get("story").size());
        for (JsonNode cause : investigation.get("causes")) {
            assertTrue(LAYERS.contains(cause.get("layer").asText()));
        }
        assertTrue(investigation.get("story").get(3).get("answer").asText().startsWith("No"),
                "with no earlier failure the honest answer is no: "
                        + investigation.get("story").get(3).get("answer").asText());
    }

    @Test
    void guardsScopeAndMembership() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Guarded Investigation Org"));

        // An evaluation that does not exist is a 404, never an empty investigation.
        call("GET", ownerToken, investigations(orgId) + "/evaluation/" + UUID.randomUUID(), null)
                .andExpect(status().isNotFound());

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("GET", outsiderToken, investigations(orgId) + "/evaluation/" + UUID.randomUUID(), null)
                .andExpect(status().isForbidden());
    }

    // ================================================================================================
    // Helpers
    // ================================================================================================

    private String investigations(UUID orgId) {
        return "/api/v1/organizations/" + orgId + "/investigations";
    }

    /** Every reference an investigation carries must point at a record the platform really holds. */
    private void assertGrounded(JsonNode investigation) {
        List<String> kinds = List.of("agent:", "prompt:", "dataset:", "evaluation:", "provider:", "project:",
                "observation:", "claim:", "decision:", "evidence:", "knowledge:", "run:",
                "prompt-version:", "agent-version:", "dataset-version:");
        JsonNode references = investigation.get("references");
        for (String panel : List.of("artifacts", "evidence", "knowledge", "decisions", "revisions",
                "precedents", "relatedEvaluations")) {
            references.get(panel).forEach(ref -> assertTrue(
                    kinds.stream().anyMatch(k -> ref.get("id").asText().startsWith(k)),
                    panel + " id must point at a real record, got " + ref.get("id").asText()));
        }
        investigation.get("timeline").forEach(event -> {
            JsonNode ref = event.get("ref");
            if (ref != null && !ref.isNull()) {
                assertTrue(kinds.stream().anyMatch(k -> ref.get("id").asText().startsWith(k)),
                        "a timeline event must open a real record, got " + ref.get("id").asText());
            }
        });
    }
}

package com.broksforge.modules.brok;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * P12 — verifies Brok answers engineering questions from the <b>real engineering record</b> and
 * obeys the constitutional epistemic contract.
 *
 * <p>The assertions here are deliberately about honesty rather than phrasing: every answer declares an
 * epistemic status, every reasoning step declares how it is known, every reference resolves to a record that
 * exists, and a question the record cannot answer produces an explicit "unknown" with zero fabricated
 * evidence. Those are the properties that make this an engineering partner rather than a chatbot.
 */
class BrokIntegrationTest extends AbstractIntegrationTest {

    private static final Set<String> EPISTEMIC = Set.of("derived", "inferred", "suggested", "unknown");
    private static final Set<String> VERDICTS = Set.of("healthy", "attention", "risk", "failed", "unknown");
    private static final Set<String> CONFIDENCE = Set.of("consistent-with", "likely", "near-certain");

    /** Every action must name a surface the product already has — Brok never invents a destination. */
    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "openGraph", "openExecutionGraph", "openFailureGraph", "openIntelligence", "openEvolution", "openRevisions",
            "compareRevisions", "openKnowledge", "openRegistry", "openEvaluation", "openAnalytics",
            "openInsights", "startInvestigation");

    @Test
    void answersEngineeringQuestionsGroundedInTheRecord() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Brok Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Brok Project"));
        String base = projectBase(orgId.toString(), projectId.toString());
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        // An agent whose endpoint refuses connections, so auto-running an evaluation produces REAL failed
        // runs without reaching the network. This is the engineering record Brok must reason over.
        String agentId = idOf(apiPost(token, base + "/agents", Map.of(
                "name", "Checkout Agent",
                "visibility", "PRIVATE",
                "framework", "CUSTOM_REST",
                "language", "PYTHON",
                "endpointUrl", "http://127.0.0.1:9/agent",
                "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Support Dataset");

        // A prompt promoted twice — a real decision, with a real superseded revision.
        String promptId = idOf(apiPost(token, base + "/prompts", Map.of("name", "Support Prompt"), 201));
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Answer {{question}}", "activate", true), 201);
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Answer {{question}} for {{user}} carefully", "activate", true), 201);

        JsonNode ran = apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Checkout Quality", "agentId", agentId, "datasetId", datasetId, "autoRun", true), 201);
        String ranJobId = ran.get("id").asText();
        assertTrue(ran.get("failedItems").asInt() > 0 || "FAILED".equals(ran.get("status").asText()),
                "the auto-run evaluation must record a real failure to reason about, got "
                        + ran.get("status").asText());

        // ---- Every answer obeys the epistemic contract ----------------------------------------------
        JsonNode failure = ask(token, brok, "Why did the deployment fail?", projectId, null);
        assertContract(failure);
        assertEquals("failure.explain", failure.get("intent").asText());
        assertEquals("question", failure.get("kind").asText());
        assertTrue(failure.get("evidence").size() >= 1, "a failure answer must cite the records it read");
        assertTrue(hasRefTo(failure, "evaluation:" + ranJobId),
                "the failure answer must reference the evaluation it was derived from");
        assertTrue(failure.get("recommendations").size() >= 1,
                "an answer must continue into an engineering action");
        assertNotNull(failure.get("recommendations").get(0).get("action").get("kind").asText());

        // ---- Promotion rationale is read from the real revision, not invented ------------------------
        JsonNode promotion = ask(token, brok, "Why was Support Prompt promoted?", projectId, null);
        assertContract(promotion);
        assertEquals("promotion.rationale", promotion.get("intent").asText());
        assertTrue(promotion.get("references").get("revisions").size() >= 1,
                "a promotion answer must reference the AI Git revision it describes");
        // No rationale was recorded on the version, so Brok must say so rather than invent one.
        assertTrue(promotion.get("verdict").get("headline").asText().contains("no reason was recorded"),
                "an unrecorded rationale must be reported as absent: "
                        + promotion.get("verdict").get("headline").asText());

        // ---- Rollback advice weighs evidence and refuses to guess ------------------------------------
        JsonNode rollback = ask(token, brok, "Should I rollback Support Prompt?", projectId, null);
        assertContract(rollback);
        assertEquals("rollback.advice", rollback.get("intent").asText());
        assertEquals("unknown", rollback.get("verdict").get("state").asText(),
                "with no evaluation of the prompt, a rollback verdict must be unknown");

        // ---- AI Git: the diff is the real field-by-field comparison ----------------------------------
        JsonNode diff = ask(token, brok, "What changed between these revisions?", projectId,
                "prompt:" + promptId);
        assertContract(diff);
        assertEquals("revision.diff", diff.get("intent").asText());
        assertTrue(reasoningText(diff).toLowerCase().contains("template"),
                "the diff must name the field that actually changed: " + reasoningText(diff));
        assertEquals(2, diff.get("references").get("revisions").size());

        // ---- Impact is read from the Forge Graph's real relationships --------------------------------
        JsonNode impact = ask(token, brok, "Show every artifact affected by Support Dataset.", projectId, null);
        assertContract(impact);
        assertEquals("impact.of", impact.get("intent").asText());
        assertTrue(impact.get("impact").get("count").asInt() >= 1,
                "the evaluation that uses this dataset depends on it");

        // ---- Unsupported decisions: the prompt promotion has no evaluation behind it ------------------
        JsonNode unsupported = ask(token, brok, "What engineering decisions remain unsupported?",
                projectId, null);
        assertContract(unsupported);
        assertEquals("decisions.unsupported", unsupported.get("intent").asText());
        assertTrue(unsupported.get("references").get("decisions").size() >= 1,
                "the promoted prompt has no evidence, so it must appear here");

        // ---- The attention queue continues into real workflows ---------------------------------------
        JsonNode next = ask(token, brok, "What should my team work on next?", projectId, null);
        assertContract(next);
        assertEquals("next.work", next.get("intent").asText());
        assertTrue(next.get("recommendations").size() >= 1);

        JsonNode risk = ask(token, brok, "Which prompt has the highest engineering risk?", projectId, null);
        assertContract(risk);
        assertEquals("risk.ranking", risk.get("intent").asText());

        JsonNode incomplete = ask(token, brok, "What investigations are still incomplete?", projectId, null);
        assertContract(incomplete);
        assertEquals("investigations.incomplete", incomplete.get("intent").asText());

        JsonNode week = ask(token, brok, "Summarize everything that happened this week.", projectId, null);
        assertContract(week);
        assertEquals("period.summary", week.get("intent").asText());

        JsonNode providers = ask(token, brok, "Which provider causes the most failures?", projectId, null);
        assertContract(providers);
        assertEquals("provider.failures", providers.get("intent").asText());

        JsonNode graph = ask(token, brok, "Why is this graph red?", projectId, "evaluation:" + ranJobId);
        assertContract(graph);
        assertEquals("execution.explain", graph.get("intent").asText());
        assertTrue(hasAction(graph, "openFailureGraph"),
                "a red graph must open already narrowed to the broken links, not as a filter to find");

        // ---- Explaining an artifact reports quality with its price (L-41) ----------------------------
        JsonNode evaluation = ask(token, brok, "Explain this evaluation.", projectId,
                "evaluation:" + ranJobId);
        assertContract(evaluation);
        assertEquals("evaluation.explain", evaluation.get("intent").asText());

        // ---- Honest refusal: nothing in the record answers this --------------------------------------
        JsonNode nonsense = ask(token, brok, "What is the capital of France?", projectId, null);
        assertContract(nonsense);
        assertEquals("unknown", nonsense.get("intent").asText());
        assertEquals("unknown", nonsense.get("verdict").get("state").asText());
        assertEquals("unknown", nonsense.get("verdict").get("status").asText());
        assertEquals(0, nonsense.get("evidence").size(),
                "an unanswerable question must cite no evidence at all");
        assertTrue(nonsense.get("followUps").size() >= 1,
                "a refusal must offer the questions the record can answer");

        // ---- Nothing is fabricated: every reference resolves to a real record kind --------------------
        for (JsonNode answer : List.of(failure, promotion, diff, impact, unsupported, next, evaluation)) {
            assertGrounded(answer);
        }

        // ---- Context and suggestions -----------------------------------------------------------------
        JsonNode suggestions = apiGet(token, brok + "/suggestions?projectId=" + projectId, 200);
        assertTrue(suggestions.size() >= 1, "the workspace must never open on an empty prompt box");
        suggestions.forEach(s -> assertFalse(s.get("question").asText().isBlank()));

        JsonNode context = apiGet(token,
                brok + "/context?projectId=" + projectId + "&focus=prompt:" + promptId, 200);
        assertEquals("Support Prompt", context.get("focus").get("label").asText());
        assertEquals("Brok Project", context.get("projectName").asText());

        // ---- The eight Engineering Briefs ------------------------------------------------------------
        JsonNode briefs = apiGet(token, brok + "/briefs?projectId=" + projectId, 200);
        assertEquals(8, briefs.size(), "all eight constitutional briefs must be offered");
        Set<String> kinds = new HashSet<>();
        briefs.forEach(brief -> kinds.add(brief.get("kind").asText()));
        assertTrue(kinds.containsAll(Set.of("daily", "deployment", "incident", "prompt", "evaluation",
                "dataset", "knowledge", "architecture")), "brief kinds were " + kinds);

        for (String kind : List.of("daily", "deployment", "incident", "prompt", "evaluation",
                "dataset", "knowledge", "architecture")) {
            JsonNode brief = apiGet(token, brok + "/brief/" + kind + "?projectId=" + projectId, 200);
            assertContract(brief);
            assertEquals("brief", brief.get("kind").asText(), kind + " must be a brief");
            assertEquals("brief." + kind, brief.get("intent").asText());
            assertGrounded(brief);
        }

        // An unknown brief is a 404, never an empty document.
        call("GET", token, brok + "/brief/not-a-brief?projectId=" + projectId, null)
                .andExpect(status().isNotFound());

        // A question scoped to a project that is not in this organization is a 404, never a silent "nothing
        // is happening".
        call("POST", token, brok + "/ask",
                Map.of("question", "What should my team work on next?", "projectId", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /**
     * The constitutional conversation: an engineer asks about a failure, then keeps going in shorthand.
     * Nothing after the first question restates the subject, and every answer still resolves to it. This is
     * the difference between a partner and a search box.
     */
    @Test
    void carriesTheConversationSoContextIsNeverRepeated() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Conversation Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Conversation Project"));
        String base = projectBase(orgId.toString(), projectId.toString());
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        String promptId = idOf(apiPost(token, base + "/prompts", Map.of("name", "Refund Prompt"), 201));
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Refund {{order}}", "activate", true, "notes", "Baseline wording."), 201);
        apiPost(token, base + "/prompts/" + promptId + "/versions",
                Map.of("template", "Refund {{order}} politely for {{user}}", "activate", true,
                        "notes", "Softer tone after complaints."), 201);

        List<Map<String, Object>> history = new ArrayList<>();

        // 1 — the subject is named once.
        JsonNode first = ask(token, brok, "Why was Refund Prompt promoted?", projectId, null, history);
        assertContract(first);
        assertEquals("promotion.rationale", first.get("intent").asText());
        assertEquals("prompt:" + promptId, first.get("context").get("focus").get("id").asText());
        assertTrue(first.get("memory").size() >= 1,
                "engineering memory must travel with an answer about a promotion");
        history.add(turn(first));

        // 2 — "show me the evidence" inherits it, and says so.
        JsonNode evidence = ask(token, brok, "Show me the evidence.", projectId, null, history);
        assertContract(evidence);
        assertEquals("evidence.show", evidence.get("intent").asText());
        assertEquals("prompt:" + promptId, evidence.get("context").get("focus").get("id").asText(),
                "the follow-up must inherit the subject rather than lose it");
        assertTrue(reasoningText(evidence).contains("carried from"),
                "Brok must declare that it carried the subject forward: " + reasoningText(evidence));
        history.add(turn(evidence));

        // 3 — "compare it with v1" still inherits it, and diffs against the live revision.
        JsonNode diff = ask(token, brok, "Compare it with v1.", projectId, null, history);
        assertContract(diff);
        assertEquals("revision.diff", diff.get("intent").asText());
        assertEquals(2, diff.get("references").get("revisions").size());
        assertTrue(reasoningText(diff).toLowerCase().contains("template"),
                "the diff must name the field that changed: " + reasoningText(diff));
        history.add(turn(diff));

        // 4 — "open the graph" is still about the same artifact.
        JsonNode graph = ask(token, brok, "Open the graph.", projectId, null, history);
        assertContract(graph);
        assertEquals("graph.view", graph.get("intent").asText());
        assertTrue(hasAction(graph, "openGraph"));
        history.add(turn(graph));

        // 5 — and so is the promotion decision.
        JsonNode promote = ask(token, brok, "Should I promote it?", projectId, null, history);
        assertContract(promote);
        assertEquals("promotion.advice", promote.get("intent").asText());
        assertEquals("prompt:" + promptId, promote.get("context").get("focus").get("id").asText());

        // 6 — the recorded reasoning is recalled verbatim, never re-worded.
        JsonNode memory = ask(token, brok, "What was the reasoning?", projectId, null, history);
        assertContract(memory);
        assertEquals("memory.why", memory.get("intent").asText());
        assertTrue(memory.get("memory").size() >= 1, "engineering memory must be carried through");
        assertTrue(reasoningText(memory).contains("Softer tone after complaints."),
                "the recorded rationale must appear unchanged: " + reasoningText(memory));
    }

    /** Every recommendation must open a real workflow, and failures must offer an investigation. */
    @Test
    void everyRecommendationContinuesIntoAWorkflow() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Workflow Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Workflow Project"));
        String base = projectBase(orgId.toString(), projectId.toString());
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        String agentId = idOf(apiPost(token, base + "/agents", Map.of(
                "name", "Refund Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/agent", "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(), "Refund Data");
        apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Refund Quality", "agentId", agentId, "datasetId", datasetId, "autoRun", true), 201);

        JsonNode failure = ask(token, brok, "What broke?", projectId, null);
        assertContract(failure);
        assertTrue(hasAction(failure, "openFailureGraph"),
                "a failure must offer the graph already narrowed to the broken links");
        assertTrue(hasAction(failure, "startInvestigation"),
                "a failure must be startable as an investigation");
        for (JsonNode rec : failure.get("recommendations")) {
            assertTrue(KNOWN_ACTIONS.contains(rec.get("action").get("kind").asText()),
                    "unknown action kind " + rec.get("action").get("kind").asText());
        }
        JsonNode investigation = null;
        for (JsonNode rec : failure.get("recommendations")) {
            if ("startInvestigation".equals(rec.get("action").get("kind").asText())) {
                investigation = rec.get("action");
            }
        }
        assertNotNull(investigation);
        assertFalse(investigation.get("question").asText().isBlank(),
                "an investigation must open with a real question");
    }

    /**
     * Part of what makes a partner different from a search box: "Has this happened before?" is answered by
     * going and looking. The first failure has no precedent and Brok must say exactly that; the second
     * failure on the same ground must surface the first — named, dated, its recorded cause compared, and
     * with what the team did about it (or the honest absence of any recorded decision).
     */
    @Test
    void investigatesPrecedentWhenAskedHasThisHappenedBefore() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Precedent Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Precedent Project"));
        String base = projectBase(orgId.toString(), projectId.toString());
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        String agentId = idOf(apiPost(token, base + "/agents", Map.of(
                "name", "Billing Agent", "visibility", "PRIVATE", "framework", "CUSTOM_REST",
                "language", "PYTHON", "endpointUrl", "http://127.0.0.1:9/agent", "authType", "NONE"), 201));
        String datasetId = createDatasetWithItems(token, orgId.toString(), projectId.toString(),
                "Billing Data");

        // The first failure: no precedent exists, and Brok must say so instead of inventing a pattern.
        String firstId = idOf(apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Billing Quality #1", "agentId", agentId, "datasetId", datasetId,
                "autoRun", true), 201));
        JsonNode novel = ask(token, brok, "Has this happened before?", projectId, "evaluation:" + firstId);
        assertContract(novel);
        assertGrounded(novel);
        assertEquals("history.similar", novel.get("intent").asText());
        assertTrue(novel.get("verdict").get("headline").asText().startsWith("No"),
                "the first failure has no precedent and Brok must say so: "
                        + novel.get("verdict").get("headline").asText());

        // The second failure on the same ground: the precedent must be found, named, dated and compared.
        String secondId = idOf(apiPost(token, base + "/evaluation-jobs", Map.of(
                "name", "Billing Quality #2", "agentId", agentId, "datasetId", datasetId,
                "autoRun", true), 201));
        JsonNode precedent = ask(token, brok, "Has this happened before?", projectId,
                "evaluation:" + secondId);
        assertContract(precedent);
        assertGrounded(precedent);
        assertEquals("history.similar", precedent.get("intent").asText());
        assertTrue(precedent.get("verdict").get("headline").asText().startsWith("Yes"),
                "the second failure has a precedent: " + precedent.get("verdict").get("headline").asText());
        assertTrue(hasRefTo(precedent, "evaluation:" + firstId),
                "the precedent itself must be cited as evidence");
        assertTrue(reasoningText(precedent).contains("Billing Quality #1"),
                "the precedent must be named: " + reasoningText(precedent));
        assertTrue(reasoningText(precedent).contains("ago"),
                "the precedent must be dated: " + reasoningText(precedent));
        assertTrue(hasAction(precedent, "openFailureGraph"),
                "a recurrence must continue into the failure graph");
        // Both runs failed against the same unreachable endpoint, so the recorded cause is identical and
        // the verdict must escalate from coincidence to recurrence.
        assertEquals("risk", precedent.get("verdict").get("state").asText(),
                "an identical recorded cause makes this a recurrence, got "
                        + precedent.get("verdict").get("state").asText());
        assertTrue(reasoningText(precedent).contains("No engineering decision was recorded")
                        || precedent.get("references").get("decisions").size() >= 1,
                "the answer must say what the team did about the precedent — or honestly report "
                        + "that nothing was recorded");
    }

    @Test
    void asksWhichOneRatherThanGuessing() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Ambiguity Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Ambiguity Project"));
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Alpha Instructions");
        createPromptWithVersion(token, orgId.toString(), projectId.toString(), "Beta Instructions");

        JsonNode answer = ask(token, brok, "Should I rollback the prompt?", projectId, null);
        assertContract(answer);
        assertEquals("unknown", answer.get("verdict").get("state").asText());
        assertTrue(answer.get("verdict").get("headline").asText().startsWith("Which one"),
                "two candidate prompts must produce a question, not a guess: "
                        + answer.get("verdict").get("headline").asText());
        assertEquals(2, answer.get("followUps").size(),
                "each candidate must become a follow-up the engineer can pick");
    }

    @Test
    void reportsAnEmptyRecordHonestly() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(token, "Empty Org"));
        UUID projectId = UUID.fromString(createProject(token, orgId.toString(), "Empty Project"));
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        JsonNode answer = ask(token, brok, "What is the biggest engineering risk right now?", projectId, null);
        assertContract(answer);
        assertEquals("unknown", answer.get("verdict").get("state").asText());
        assertEquals(0, answer.get("evidence").size());
        assertTrue(answer.get("verdict").get("consequence").asText().contains("Register"),
                "an empty record must teach the next step");
    }

    @Test
    void rejectsABlankQuestionAndForbidsNonMembers() throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        UUID orgId = UUID.fromString(createOrg(ownerToken, "Guarded Org"));
        String brok = "/api/v1/organizations/" + orgId + "/brok";

        call("POST", ownerToken, brok + "/ask", Map.of("question", " "))
                .andExpect(status().isBadRequest());

        String outsiderToken = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
        call("POST", outsiderToken, brok + "/ask", Map.of("question", "What should my team work on next?"))
                .andExpect(status().isForbidden());
        call("GET", outsiderToken, brok + "/suggestions", null).andExpect(status().isForbidden());
        call("GET", outsiderToken, brok + "/briefs", null).andExpect(status().isForbidden());
        call("GET", outsiderToken, brok + "/brief/daily", null).andExpect(status().isForbidden());
        call("GET", outsiderToken, brok + "/context", null).andExpect(status().isForbidden());
    }

    // ================================================================================================
    // Helpers
    // ================================================================================================

    private JsonNode ask(String token, String brok, String question, UUID projectId, String focus)
            throws Exception {
        return ask(token, brok, question, projectId, focus, List.of());
    }

    /** Asks with the conversation behind it, exactly as the workspace does. */
    private JsonNode ask(String token, String brok, String question, UUID projectId, String focus,
                         List<Map<String, Object>> history) throws Exception {
        Map<String, Object> request =
                body("question", question, "projectId", projectId, "focus", focus, "history", history);
        return apiPost(token, brok + "/ask", request, 200);
    }

    /** One prior turn, in the shape the workspace sends. */
    private Map<String, Object> turn(JsonNode answer) {
        JsonNode focus = answer.get("context").get("focus");
        return body("question", answer.get("question").asText(),
                "intent", answer.get("intent").asText(),
                "focus", focus == null || focus.isNull() ? null : focus.get("id").asText());
    }

    /** The constitutional contract every answer and every brief must satisfy. */
    private void assertContract(JsonNode answer) {
        assertFalse(answer.get("id").asText().isBlank());
        JsonNode verdict = answer.get("verdict");
        assertNotNull(verdict, "an answer must carry a verdict");
        assertTrue(VERDICTS.contains(verdict.get("state").asText()),
                "verdict state was " + verdict.get("state").asText());
        assertTrue(EPISTEMIC.contains(verdict.get("status").asText()),
                "every statement must declare how it is known, got " + verdict.get("status").asText());
        assertTrue(CONFIDENCE.contains(verdict.get("confidence").asText()),
                "confidence must be a verbal ladder step, got " + verdict.get("confidence").asText());
        assertFalse(verdict.get("headline").asText().isBlank());
        assertFalse(verdict.get("basis").asText().isBlank(), "an answer must say what it was derived from");
        assertFalse(verdict.get("headline").asText().toLowerCase().contains("i think"),
                "Brok never says 'I think'");

        answer.get("reasoning").forEach(step -> {
            assertTrue(EPISTEMIC.contains(step.get("status").asText()),
                    "every reasoning step declares its epistemic status");
            assertFalse(step.get("basis").asText().isBlank(),
                    "every reasoning step declares what it was read from");
            assertFalse(step.get("text").asText().isBlank());
        });

        assertNotNull(answer.get("impact"));
        assertNotNull(answer.get("references").get("artifacts"));
        assertNotNull(answer.get("context").get("graphNodeIds"));

        answer.get("recommendations").forEach(rec -> {
            assertFalse(rec.get("title").asText().isBlank());
            assertFalse(rec.get("why").asText().isBlank(), "a recommendation must carry its reasoning");
            assertFalse(rec.get("impact").asText().isBlank(), "a recommendation must carry its impact");
            assertTrue(CONFIDENCE.contains(rec.get("confidence").asText()));
            assertTrue(EPISTEMIC.contains(rec.get("status").asText()));
            assertFalse(rec.get("action").get("kind").asText().isBlank(),
                    "a recommendation must continue into a workflow");
            assertTrue(KNOWN_ACTIONS.contains(rec.get("action").get("kind").asText()),
                    "unknown action kind " + rec.get("action").get("kind").asText());
        });
    }

    /** Every reference must be a pointer at something the platform really holds. */
    private void assertGrounded(JsonNode answer) {
        List<String> kinds = List.of("agent:", "prompt:", "dataset:", "evaluation:", "provider:", "project:",
                "observation:", "claim:", "decision:", "evidence:", "knowledge:", "run:",
                "prompt-version:", "agent-version:", "dataset-version:");
        answer.get("evidence").forEach(ref -> assertTrue(
                kinds.stream().anyMatch(k -> ref.get("id").asText().startsWith(k)),
                "evidence id must point at a real record, got " + ref.get("id").asText()));
        JsonNode references = answer.get("references");
        for (String panel : List.of("artifacts", "knowledge", "decisions", "evaluations", "revisions")) {
            references.get(panel).forEach(ref -> assertTrue(
                    kinds.stream().anyMatch(k -> ref.get("id").asText().startsWith(k)),
                    panel + " id must point at a real record, got " + ref.get("id").asText()));
        }
    }

    private boolean hasRefTo(JsonNode answer, String id) {
        for (JsonNode ref : answer.get("evidence")) {
            if (ref.get("id").asText().equals(id)) {
                return true;
            }
        }
        for (JsonNode ref : answer.get("references").get("evaluations")) {
            if (ref.get("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAction(JsonNode answer, String kind) {
        for (JsonNode rec : answer.get("recommendations")) {
            if (kind.equals(rec.get("action").get("kind").asText())) {
                return true;
            }
        }
        return false;
    }

    private String reasoningText(JsonNode answer) {
        StringBuilder sb = new StringBuilder();
        answer.get("reasoning").forEach(step -> sb.append(step.get("text").asText()).append(' '));
        return sb.toString();
    }
}

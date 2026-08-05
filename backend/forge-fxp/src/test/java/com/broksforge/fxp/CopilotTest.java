package com.broksforge.fxp;

import com.broksforge.fxp.copilot.ForgeCopilot;
import com.broksforge.fxp.copilot.GroundedAnswer;
import com.broksforge.fxp.copilot.Intent;
import com.broksforge.fxp.copilot.LanguageModel;
import com.broksforge.fxp.copilot.TemplateLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Copilot grounding contract: the LLM explains, FKGE proves — and refuses rather than invents. */
class CopilotTest {

    /** A language model that fails the test if it is ever consulted — used to prove refusals never call it. */
    private static final LanguageModel FORBIDDEN = ctx -> {
        throw new AssertionError("the language model must not be consulted for an ungrounded question");
    };

    @Test
    @DisplayName("a grounded question is answered by narrating an FKGE proof; the answer carries the proof")
    void groundedAnswerCarriesProof() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCopilot copilot = s.client.copilot(new TemplateLanguageModel());

        GroundedAnswer a = copilot.ask(s.agent.node(), Intent.PROVENANCE);
        assertTrue(a.grounded());
        assertFalse(a.proof().empty(), "the proof must contain the platform facts");
        assertTrue(a.proof().citations().contains(s.provider.node()), "the proof cites the provider");
        assertTrue(a.narrative().contains("derives from"), "the narration reflects the proof");
    }

    @Test
    @DisplayName("an ungrounded question is refused WITHOUT ever consulting the language model")
    void refusesUngroundedWithoutCallingModel() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCopilot copilot = s.client.copilot(FORBIDDEN); // throws if narrated

        // A provider is a primary artifact: it has no supporting evidence to collect.
        GroundedAnswer a = copilot.ask(s.provider.node(), Intent.EVIDENCE);
        assertFalse(a.grounded());
        assertTrue(a.proof().empty());
        assertTrue(a.narrative().toLowerCase().contains("cannot answer"));
    }

    @Test
    @DisplayName("an unknown subject is refused, not hallucinated")
    void refusesUnknownSubject() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCopilot copilot = s.client.copilot(FORBIDDEN);
        com.broksforge.kernel.api.NodeId ghost =
                com.broksforge.kernel.api.NodeId.of(java.util.UUID.fromString("0000dead-0000-4000-8000-000000000000"));
        GroundedAnswer a = copilot.ask(ghost, Intent.WHY);
        assertFalse(a.grounded());
    }

    @Test
    @DisplayName("grounded answers are deterministic and reproducible (same narrative and asOf)")
    void deterministicAnswers() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ForgeCopilot copilot = s.client.copilot(new TemplateLanguageModel());
        GroundedAnswer first = copilot.ask(s.deployment.node(), Intent.WHY_IN_PRODUCTION);
        GroundedAnswer second = copilot.ask(s.deployment.node(), Intent.WHY_IN_PRODUCTION);
        assertEquals(first.narrative(), second.narrative());
        assertEquals(first.asOf(), second.asOf());
        assertTrue(first.grounded());
    }
}

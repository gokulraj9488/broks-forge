package com.broksforge.fxp;

import com.broksforge.fxp.integrate.ExternalCommit;
import com.broksforge.fxp.integrate.GitSourceControlAdapter;
import com.broksforge.fxp.integrate.LocalModelProviderAdapter;
import com.broksforge.fxp.integrate.ModelInvocation;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integrations are edge adapters: they translate external events into lawful platform facts, one-way. */
class IntegrationAdapterTest {

    @Test
    @DisplayName("a source-control adapter records an external commit as a Forge artifact")
    void sourceControlAdapterRecordsArtifact() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        KnowledgeObject imported = new GitSourceControlAdapter().ingest(s.studio,
                new ExternalCommit("abc123", "prompts/system.txt", "be concise", "tune prompt", "dev@x"));
        assertEquals(ObjectTypes.PROMPT, imported.type());
        // it is now a first-class platform fact, explainable like any other
        assertTrue(s.client.explorer().explain(imported.node()) != null);
    }

    @Test
    @DisplayName("a model-provider adapter records an invocation as a Run observation executing an agent")
    void modelAdapterRecordsRun() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        KnowledgeObject run = new LocalModelProviderAdapter().record(s.studio, s.agent,
                new ModelInvocation("sonnet-5", "hello", "hi", "success"));
        assertEquals(ObjectTypes.RUN, run.type());
        // the run's provenance reaches the agent it executed
        assertTrue(s.client.explorer().provenance(run.node()).contains(s.agent.node()));
    }
}

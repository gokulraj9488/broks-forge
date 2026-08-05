package com.broksforge.platform.provider;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.model.ModelInvocationRequest;
import com.broksforge.modules.model.ModelInvocationResult;
import com.broksforge.platform.ForgePlatform;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the P1 recording path: dormant unless a lawful {@link AgentAnchorResolver} supplies an anchor, and
 * — when one does (simulating P2) — it records a lawful {@code Run}. The test-only resolver builds the
 * lawful {@code Provider → Model → Prompt → Agent} chain; production P1 ships no resolver, so it stays
 * dormant.
 */
@Import(ForgeProviderBridgeIntegrationTest.TestAnchors.class)
class ForgeProviderBridgeIntegrationTest extends AbstractIntegrationTest {

    private static final String ANCHOR_MODEL = "__anchor__";

    @Autowired
    private ForgeProviderBridge bridge;

    @Autowired
    private ForgePlatform platform;

    @TestConfiguration
    static class TestAnchors {
        /** Simulates the P2 resolver: builds a lawful Agent chain only for the sentinel model. */
        @Bean
        AgentAnchorResolver testAnchorResolver() {
            return (request, client) -> {
                if (!ANCHOR_MODEL.equals(request.model())) {
                    return Optional.empty(); // decline — the bridge must then record nothing
                }
                KnowledgeObject provider = client.studio().create(ObjectTypes.PROVIDER, obj("name", "anchor-provider"));
                KnowledgeObject model = client.studio().create(ObjectTypes.MODEL, obj("model_id", "anchor-model"),
                        Link.of(Verbs.USES, provider));
                KnowledgeObject prompt = client.studio().create(ObjectTypes.PROMPT, obj("text", "anchor-prompt"));
                KnowledgeObject agent = client.studio().create(ObjectTypes.AGENT,
                        CanonicalValue.objectBuilder().put("name", "anchor-agent").build(),
                        Link.of(Verbs.USES, model), Link.of(Verbs.USES, prompt));
                return Optional.of(agent);
            };
        }

        static CanonicalValue obj(String k, String v) {
            return CanonicalValue.objectBuilder().put(k, v).build();
        }
    }

    @Test
    void recordsLawfulRunWhenAnchorAvailable() {
        UUID orgId = UUID.randomUUID();
        ModelInvocationRequest request = new ModelInvocationRequest(
                orgId, UUID.randomUUID(), LlmProvider.OPENAI, ANCHOR_MODEL, "hello", Map.of(), null);
        ModelInvocationResult result = new ModelInvocationResult(true, "hi", 200, 12L, null, null, null, null, null);

        bridge.onInvocation(request, result);

        ForgeClient client = clientFor(orgId);
        List<KnowledgeObject> runs = client.repository().knowledge().view().objects(ObjectTypes.RUN);
        assertFalse(runs.isEmpty(), "a lawful Run must be recorded when an anchor is available");
        // the Run's provenance must reach the Agent it executed (proves executed→Agent was lawful)
        boolean reachesAgent = client.engine().provenanceOf(runs.get(0).node()).ancestors().stream()
                .anyMatch(n -> n.kind() == Kind.ARTIFACT && "Agent".equals(n.typeName().orElse("")));
        assertTrue(reachesAgent, "the recorded Run must be anchored to an Agent");
    }

    @Test
    void recordsNothingWhenResolverDeclines() {
        UUID orgId = UUID.randomUUID();
        ModelInvocationRequest request = new ModelInvocationRequest(
                orgId, UUID.randomUUID(), LlmProvider.OPENAI, "ordinary-model", "hello", Map.of(), null);
        ModelInvocationResult result = new ModelInvocationResult(true, "hi", 200, 12L, null, null, null, null, null);

        bridge.onInvocation(request, result);

        ForgeClient client = clientFor(orgId);
        List<KnowledgeObject> runs = client.repository().knowledge().view().objects(ObjectTypes.RUN);
        assertTrue(runs.isEmpty(), "no Run may be recorded without a lawful anchor (the P1 dormant guarantee)");
    }

    private ForgeClient clientFor(UUID orgId) {
        OrgId org = platform.identity().toOrgId(orgId);
        return platform.clientFor(org, ForgePlatform.SYSTEM_ACTOR);
    }
}

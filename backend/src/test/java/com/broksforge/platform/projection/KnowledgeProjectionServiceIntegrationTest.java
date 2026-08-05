package com.broksforge.platform.projection;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.platform.ForgePlatform;
import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves lawful, idempotent projection of V1 entities into Platform V2 Knowledge, and that execution truth
 * (Evaluation + EvaluationVerdict) is recordable lawfully with no Agent — via {@code measured_by → Evaluation}.
 */
class KnowledgeProjectionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeProjectionService projection;

    @Autowired
    private ForgePlatform platform;

    private ForgeClient client(UUID orgId) {
        OrgId org = platform.identity().toOrgId(orgId);
        return platform.clientFor(org, ActorId.of("test:projection"));
    }

    @Test
    void projectionIsIdempotent() {
        UUID orgId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        ForgeClient client = client(orgId);

        KnowledgeObject first = projection.projectProvider(client, providerId, "anthropic");
        KnowledgeObject second = projection.projectProvider(client(orgId), providerId, "anthropic");
        assertEquals(first.node(), second.node(), "re-projecting the same entity must not duplicate the artifact");
    }

    @Test
    void projectsLawfulConfigurationAndExecutionTruth() {
        UUID orgId = UUID.randomUUID();
        ForgeClient client = client(orgId);

        UUID providerId = UUID.randomUUID();
        KnowledgeObject provider = projection.projectProvider(client, providerId, "anthropic");
        KnowledgeObject model = projection.projectModel(client, providerId, provider, "sonnet-5");
        KnowledgeObject dataset = projection.projectDataset(client, UUID.randomUUID(), "sha-256:abc123");
        KnowledgeObject evaluation = projection.projectEvaluation(client, UUID.randomUUID(),
                List.of("exact_match", "latency"), List.of(dataset));
        KnowledgeObject verdict = projection.projectVerdict(client, UUID.randomUUID().toString(),
                "passes acceptance suite", "offline-eval", new BigDecimal("0.92"), evaluation);

        // Model uses Provider (composition → provenance)
        assertTrue(client.engine().provenanceOf(model.node()).contains(provider.node()));
        // Evaluation uses Dataset
        assertTrue(client.engine().provenanceOf(evaluation.node()).contains(dataset.node()));
        // Verdict measured_by Evaluation (evidence → provenance); the claim exists, so the Claim Law held
        assertTrue(client.engine().provenanceOf(verdict.node()).contains(evaluation.node()));
        var confidence = client.engine().confidenceOf(verdict.node());
        assertTrue(confidence.defined());
        assertEquals(0, confidence.confidence().compareTo(new BigDecimal("0.92")));
    }
}

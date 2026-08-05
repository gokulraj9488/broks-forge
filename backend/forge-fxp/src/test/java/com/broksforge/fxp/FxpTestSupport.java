package com.broksforge.fxp;

import com.broksforge.fvcs.repo.Repository;
import com.broksforge.fxp.studio.StudioService;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A realistic AI-engineering scenario built entirely through the FXP authoring experience (Studio) — proving
 * the write path end-to-end — over a fresh in-memory platform.
 */
final class FxpTestSupport {

    static final OrgId ORG = OrgId.of(UUID.fromString("0000c0de-0000-4000-8000-00000000fabc"));
    static final ActorId ACTOR = ActorId.of("engineer:fxp-test");

    private FxpTestSupport() {}

    static ForgeClient client() {
        return ForgeClient.open(Repository.open(Kernels.inMemory(), ORG, ACTOR), ACTOR);
    }

    static CanonicalValue obj(String k, String v) {
        return CanonicalValue.objectBuilder().put(k, v).build();
    }

    /** The scenario, authored via Studio; exposes the objects and their continuant ids. */
    static final class Scenario {
        final ForgeClient client;
        final StudioService studio;
        final KnowledgeObject provider, model, prompt, agent, run, verdict, benchmark, env, deployment, incident;

        Scenario() {
            this.client = client();
            this.studio = client.studio();

            provider = studio.create(ObjectTypes.PROVIDER, obj("name", "anthropic"));
            model = studio.create(ObjectTypes.MODEL, obj("model_id", "sonnet-5"), Link.of(Verbs.USES, provider));
            prompt = studio.create(ObjectTypes.PROMPT, obj("text", "you are a helpful assistant"));
            agent = studio.create(ObjectTypes.AGENT,
                    CanonicalValue.objectBuilder().put("name", "support-bot").build(),
                    Link.of(Verbs.USES, model), Link.of(Verbs.USES, prompt));
            run = studio.recordObservation(ObjectTypes.RUN, obj("status", "success"), Link.of(Verbs.EXECUTED, agent));

            verdict = studio.authorClaim(ObjectTypes.EVALUATION_VERDICT,
                    "passes acceptance suite", "offline-eval", new BigDecimal("0.90"), Link.of(Verbs.CITES, run));
            benchmark = studio.authorClaim(ObjectTypes.BENCHMARK_SCORE,
                    "beats baseline by 4%", "held-out-benchmark", new BigDecimal("0.60"), Link.of(Verbs.CITES, run));

            env = studio.create(ObjectTypes.ENVIRONMENT,
                    CanonicalValue.objectBuilder().put("name", "prod").put("tier", "production").build());
            deployment = studio.recordDecision(ObjectTypes.DEPLOYMENT, "ship support-bot to prod", false,
                    Link.of(Verbs.APPLIED, agent), Link.of(Verbs.TARGETS, env),
                    Link.of(Verbs.RESTS_ON, verdict), Link.of(Verbs.RESTS_ON, benchmark));

            incident = studio.recordObservation(ObjectTypes.INCIDENT, obj("severity", "high"));
            studio.link(deployment, Verbs.CAUSED, incident);
        }
    }

    static Scenario scenario() {
        return new Scenario();
    }
}

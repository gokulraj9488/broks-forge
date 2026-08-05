package com.broksforge.fkge;

import com.broksforge.fvcs.repo.Repository;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A realistic AI-engineering scenario on a fresh in-memory kernel, built entirely through public write APIs.
 *
 * <pre>
 *   provider(anthropic) ← model(sonnet) ← agent → prompt(system)
 *   run --executed--> agent
 *   verdict(0.90) --cites--> run          benchmark(0.60) --cites--> run
 *   deployment --applied--> agent, --targets--> env(prod), --rests_on--> {verdict, benchmark}
 *   deployment --caused--> incident(high)
 * </pre>
 */
final class TestSupport {

    static final OrgId ORG = OrgId.of(UUID.fromString("0000c0de-0000-4000-8000-00000000f6e4"));
    static final ActorId ACTOR = ActorId.of("engineer:fkge-test");

    private TestSupport() {}

    static Repository repo() {
        return Repository.open(Kernels.inMemory(), ORG, ACTOR);
    }

    /** A built scenario, exposing the continuant ids reasoning is asked about. */
    static final class Scenario {
        final Repository repo;
        final KnowledgeGraph kg;
        final NodeId provider, model, prompt, agent, run, verdict, benchmark, env, deployment, incident;

        Scenario(Repository repo) {
            this.repo = repo;
            this.kg = repo.knowledge();

            KnowledgeObject providerObj = kg.define(ObjectTypes.PROVIDER, obj("name", "anthropic"));
            KnowledgeObject modelObj = kg.define(ObjectTypes.MODEL, obj("model_id", "sonnet-5"),
                    Link.of(Verbs.USES, providerObj));
            KnowledgeObject promptObj = kg.define(ObjectTypes.PROMPT, obj("text", "you are a helpful assistant"));
            KnowledgeObject agentObj = kg.define(ObjectTypes.AGENT,
                    CanonicalValue.objectBuilder().put("name", "support-bot").build(),
                    Link.of(Verbs.USES, modelObj), Link.of(Verbs.USES, promptObj));
            KnowledgeObject runObj = kg.define(ObjectTypes.RUN, obj("status", "success"),
                    Link.of(Verbs.EXECUTED, agentObj));

            KnowledgeObject verdictObj = kg.define(ObjectTypes.EVALUATION_VERDICT,
                    claim("passes acceptance suite", "offline-eval", "0.90"),
                    Link.of(Verbs.CITES, runObj));
            KnowledgeObject benchmarkObj = kg.define(ObjectTypes.BENCHMARK_SCORE,
                    claim("beats baseline by 4%", "held-out-benchmark", "0.60"),
                    Link.of(Verbs.CITES, runObj));

            KnowledgeObject envObj = kg.define(ObjectTypes.ENVIRONMENT,
                    CanonicalValue.objectBuilder().put("name", "prod").put("tier", "production").build());
            KnowledgeObject deploymentObj = kg.define(ObjectTypes.DEPLOYMENT,
                    CanonicalValue.objectBuilder().put("statement", "ship support-bot to prod").build(),
                    Link.of(Verbs.APPLIED, agentObj), Link.of(Verbs.TARGETS, envObj),
                    Link.of(Verbs.RESTS_ON, verdictObj), Link.of(Verbs.RESTS_ON, benchmarkObj));

            KnowledgeObject incidentObj = kg.define(ObjectTypes.INCIDENT, obj("severity", "high"));
            kg.relate(deploymentObj, Verbs.CAUSED, incidentObj); // extrinsic causality edge

            this.provider = providerObj.node();
            this.model = modelObj.node();
            this.prompt = promptObj.node();
            this.agent = agentObj.node();
            this.run = runObj.node();
            this.verdict = verdictObj.node();
            this.benchmark = benchmarkObj.node();
            this.env = envObj.node();
            this.deployment = deploymentObj.node();
            this.incident = incidentObj.node();
        }
    }

    static Scenario scenario() {
        return new Scenario(repo());
    }

    static CanonicalValue obj(String k, String v) {
        return CanonicalValue.objectBuilder().put(k, v).build();
    }

    static CanonicalValue claim(String statement, String method, String confidence) {
        return CanonicalValue.objectBuilder()
                .put("statement", statement)
                .put("method", method)
                .put("confidence", CanonicalValue.of(new BigDecimal(confidence)))
                .build();
    }

    /** The log position at which {@code node} was first created — for time-travel tests. */
    static LogPosition positionOf(Repository repo, NodeId node) {
        for (LogEntry e : repo.kernel().log(ORG)) {
            if (e.payload() instanceof Payload.NodePut np && np.node().equals(node)) return e.position();
        }
        throw new AssertionError("no NodePut for " + node);
    }
}

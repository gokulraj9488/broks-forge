package com.broksforge.fxp.copilot;

import com.broksforge.fkge.KnowledgeGraphEngine;
import com.broksforge.fkge.depend.DependencySet;
import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.explain.ExplanationStep;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge Copilot — AI-assisted engineering under a hard contract: <b>the LLM explains, FKGE proves</b>.
 *
 * <p>For every question the Copilot (1) computes the proof from FKGE, (2) <b>refuses</b> if the proof is
 * empty — without ever consulting the language model — and (3) only then hands the facts to the model to
 * narrate. The {@link LanguageModel} never sees the graph, so it cannot invent engineering truth; every
 * answer carries its machine-checkable {@link Proof}.
 */
public final class ForgeCopilot {

    private final java.util.function.Supplier<KnowledgeGraphEngine> engines;
    private final LanguageModel model;

    public ForgeCopilot(java.util.function.Supplier<KnowledgeGraphEngine> engines, LanguageModel model) {
        this.engines = engines;
        this.model = model;
    }

    public GroundedAnswer ask(NodeId subject, Intent intent) {
        KnowledgeGraphEngine fkge = engines.get();
        LogPosition asOf = fkge.index().position();
        if (fkge.index().node(subject).isEmpty()) {
            return GroundedAnswer.refusal(subject, intent,
                    "I cannot answer this: the subject is not known to the platform.", asOf);
        }
        Proof proof = prove(fkge, subject, intent, asOf);
        if (proof.empty()) {
            return GroundedAnswer.refusal(subject, intent,
                    "I cannot answer this from the platform: no supporting facts were found.", asOf);
        }
        // Only a non-empty proof reaches the language model, and only its facts do.
        String narrative = model.narrate(new GroundingContext(intent, label(fkge, subject), proof.facts()));
        return new GroundedAnswer(subject, intent, true, narrative, proof, asOf);
    }

    private Proof prove(KnowledgeGraphEngine fkge, NodeId subject, Intent intent, LogPosition asOf) {
        List<String> facts = new ArrayList<>();
        List<NodeId> cites = new ArrayList<>();
        switch (intent) {
            case PROVENANCE -> {
                Provenance p = fkge.provenanceOf(subject);
                for (GraphNode a : p.ancestors()) {
                    facts.add("derives from " + a.label());
                    cites.add(a.id());
                }
            }
            case IMPACT -> {
                Impact im = fkge.impactOf(subject);
                for (GraphNode d : im.dependents()) {
                    facts.add("affects " + d.label());
                    cites.add(d.id());
                }
            }
            case DEPENDENCIES -> {
                DependencySet ds = fkge.dependenciesOf(subject);
                for (GraphNode d : ds.nodes()) {
                    facts.add("depends on " + d.label());
                    cites.add(d.id());
                }
            }
            case ROOT_CAUSE -> {
                CausalTrace t = fkge.rootCause(subject);
                for (GraphNode c : t.causes()) {
                    facts.add("caused by " + c.label());
                    cites.add(c.id());
                }
                if (!t.sound()) facts.add("WARNING: causal-order anomaly detected (" + t.anomalies().size() + ")");
            }
            case CONFIDENCE -> {
                ConfidenceResult c = fkge.confidenceOf(subject);
                if (c.defined()) {
                    facts.add("confidence " + c.confidence() + " (bounded by the weakest supporting claim)");
                    if (c.weakestLink() != null) cites.add(c.weakestLink());
                }
            }
            case EVIDENCE -> {
                for (GraphNode e : fkge.evidenceFor(subject)) {
                    facts.add("evidenced by " + e.label());
                    cites.add(e.id());
                }
            }
            case WHY -> {
                Explanation e = fkge.explain(subject);
                for (ExplanationStep s : e.steps()) {
                    facts.add("because " + s.verb().name() + " → " + label(fkge, s.to()));
                    cites.add(s.to());
                }
                if (!e.complete()) facts.add("NOTE: explanation is incomplete (" + e.gaps().size() + " gap(s))");
            }
            case WHY_IN_PRODUCTION -> {
                Provenance p = fkge.provenanceOf(subject);
                Explanation e = fkge.explain(subject);
                ConfidenceResult c = fkge.confidenceOf(subject);
                Impact im = fkge.impactOf(subject);
                if (!p.ancestors().isEmpty()) {
                    facts.add("provenance: " + p.ancestors().size() + " ancestor(s), certified " + p.certificate());
                    p.ancestors().forEach(a -> cites.add(a.id()));
                }
                if (!e.steps().isEmpty()) {
                    facts.add("decision proof: " + e.steps().size() + " step(s), complete=" + e.complete());
                }
                if (c.defined()) facts.add("confidence: " + c.confidence());
                facts.add("blast radius: " + im.radius() + " dependent(s)");
            }
        }
        return new Proof(intent, subject, facts, cites, asOf);
    }

    private String label(KnowledgeGraphEngine fkge, NodeId id) {
        return fkge.index().node(id).map(GraphNode::label).orElse(id.toString());
    }
}

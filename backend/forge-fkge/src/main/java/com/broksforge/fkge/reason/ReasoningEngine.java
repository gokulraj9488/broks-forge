package com.broksforge.fkge.reason;

import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.index.Order;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.Reach;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Deterministic reasoning that is arithmetic, not inference: conservative confidence propagation ({@code min}
 * of supporting claims) and causal tracing with a log-position soundness check. No probability, no priors.
 */
public final class ReasoningEngine {

    private final GraphIndex index;
    private final TraversalEngine traversal;

    public ReasoningEngine(TraversalEngine traversal) {
        this.traversal = traversal;
        this.index = traversal.index();
    }

    /**
     * The {@code min}-bound confidence over the claims a node rests on (evidence ∪ intent, upstream). A
     * conjunction is at most as strong as its weakest supporting claim. Observations are 1; an Artifact with
     * no supporting claims is not a truth-bearer (undefined). {@code min} assumes nothing — the product rule
     * is rejected because it assumes independence.
     */
    public ConfidenceResult confidenceOf(NodeId n) {
        GraphNode g = index.node(n).orElseThrow(() -> new IllegalArgumentException("unknown node: " + n));

        Reach r = traversal.closure(n, EnumSet.of(EdgeFamily.EVIDENCE, EdgeFamily.INTENT), Direction.OUT, -1);
        List<GraphNode> support = new ArrayList<>(r.nodes().stream().filter(x -> x.kind() == Kind.CLAIM).toList());

        if (support.isEmpty()) {
            return switch (g.kind()) {
                case CLAIM -> new ConfidenceResult(n, confidence(g), n, List.of(g), index.position());
                case OBSERVATION -> new ConfidenceResult(n, BigDecimal.ONE, n, List.of(g), index.position());
                default -> new ConfidenceResult(n, null, null, List.of(), index.position()); // not a truth-bearer
            };
        }
        support.sort(Order.NODES);
        GraphNode weakest = support.stream()
                .min(Comparator.comparing((GraphNode x) -> confidence(x)).thenComparing(Order.NODES))
                .orElseThrow();
        return new ConfidenceResult(n, confidence(weakest), weakest.id(), support, index.position());
    }

    /**
     * The causes of an effect: the upstream (IN) causal closure, ordered by the causal clock. A putative
     * cause with a later log position than its effect is reported as an anomaly, never accepted as causation.
     */
    public CausalTrace rootCause(NodeId effect) {
        GraphNode e = index.node(effect).orElseThrow(() -> new IllegalArgumentException("unknown node: " + effect));
        Reach r = traversal.closure(effect, Lenses.CAUSES.families(), Direction.IN, -1);
        List<GraphNode> causes = r.others().stream()
                .sorted(Comparator.comparingLong((GraphNode g) -> g.position().value()).thenComparing(Order.NODES))
                .toList();
        LogPosition effPos = e.position();
        List<String> anomalies = new ArrayList<>();
        for (GraphNode c : causes) {
            if (c.position().compareTo(effPos) > 0) {
                anomalies.add("putative cause " + c.label() + " (" + c.id() + ") occurs after the effect: log position "
                        + c.position().value() + " > " + effPos.value());
            }
        }
        return new CausalTrace(effect, causes, anomalies, index.position());
    }

    private BigDecimal confidence(GraphNode g) {
        if (g.payload() instanceof CanonicalValue.Obj o) {
            CanonicalValue v = o.entries().get("confidence");
            if (v instanceof CanonicalValue.Num num) return num.value();
        }
        return BigDecimal.ZERO; // missing confidence on a claim is a law violation → most conservative bound
    }
}

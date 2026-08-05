package com.broksforge.fkge;

import com.broksforge.fkge.depend.DependencyEngine;
import com.broksforge.fkge.depend.DependencySet;
import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.explain.ExplanationEngine;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.impact.ImpactEngine;
import com.broksforge.fkge.impact.Influence;
import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.project.ProjectionEngine;
import com.broksforge.fkge.project.Summary;
import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.provenance.ProvenanceEngine;
import com.broksforge.fkge.query.Lens;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.fkge.reason.ReasoningEngine;
import com.broksforge.fkge.search.Pattern;
import com.broksforge.fkge.search.SearchEngine;
import com.broksforge.fkge.spi.LensModule;
import com.broksforge.fkge.spi.LensRegistry;
import com.broksforge.fkge.traverse.Neighborhood;
import com.broksforge.fkge.traverse.Path;
import com.broksforge.fkge.traverse.TraversalEngine;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.Repository;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.knowledge.ontology.Ontology;

import java.util.List;
import java.util.Optional;

/**
 * The Forge Knowledge Graph Engine — the public façade and Query Engine. It compiles engineering questions
 * ("why was this approved?", "what depends on this dataset?", "what caused this incident?") into the
 * deterministic six-primitive algebra over the graph the kernel already stores.
 *
 * <p>Read-only and stateless over an immutable {@link GraphIndex}: it stores nothing, writes nothing, and
 * modifies no frozen layer. Every result carries the {@link LogPosition} it was computed at, so every answer
 * is reproducible and independently re-verifiable.
 */
public final class KnowledgeGraphEngine {

    private final GraphIndex index;
    private final Repository repository; // present when opened over FVCS; enables version questions
    private final LensRegistry lenses;

    private final TraversalEngine traversal;
    private final ProvenanceEngine provenance;
    private final DependencyEngine dependency;
    private final ImpactEngine impact;
    private final ExplanationEngine explanation;
    private final ReasoningEngine reasoning;
    private final SearchEngine search;
    private final ProjectionEngine projection;

    private KnowledgeGraphEngine(GraphIndex index, Repository repository, LensRegistry lenses) {
        this.index = index;
        this.repository = repository;
        this.lenses = lenses;
        this.traversal = new TraversalEngine(index);
        this.provenance = new ProvenanceEngine(traversal);
        this.dependency = new DependencyEngine(traversal);
        this.impact = new ImpactEngine(traversal);
        this.explanation = new ExplanationEngine(traversal);
        this.reasoning = new ReasoningEngine(traversal);
        this.search = new SearchEngine(index);
        this.projection = new ProjectionEngine(index, provenance, dependency, impact, reasoning);
    }

    // ---- Construction ----

    /** Open over an FVCS repository (recommended): version, snapshot, branch, and history nodes are included. */
    public static KnowledgeGraphEngine open(Repository repo, LensModule... extensions) {
        if (repo == null) throw new IllegalArgumentException("repo");
        return new KnowledgeGraphEngine(GraphIndex.of(repo), repo, registry(extensions));
    }

    /** Open over a kernel + org + (composed) ontology, without version-control questions. */
    public static KnowledgeGraphEngine open(ForgeKernel kernel, com.broksforge.kernel.api.OrgId org,
                                            Ontology ontology, LensModule... extensions) {
        return new KnowledgeGraphEngine(GraphIndex.of(kernel, org, ontology), null, registry(extensions));
    }

    private static LensRegistry registry(LensModule... extensions) {
        LensRegistry r = LensRegistry.withBuiltins();
        if (extensions != null) for (LensModule m : extensions) if (m != null) m.contribute(r);
        return r;
    }

    /** Re-bind the engine to an earlier log prefix for deterministic time-travel reasoning. */
    public KnowledgeGraphEngine asOf(LogPosition position) {
        GraphIndex past = GraphIndex.asOf(index.kernel(), index.org(), index.ontology(), position);
        return new KnowledgeGraphEngine(past, repository, lenses);
    }

    // ---- Provenance / dependency / impact ----

    public Provenance provenanceOf(NodeId n) {
        return provenance.of(n);
    }

    public List<GraphNode> lineageOf(NodeId n) {
        return provenance.lineage(n);
    }

    public DependencySet dependenciesOf(NodeId n) {
        return dependency.of(n);
    }

    public Impact impactOf(NodeId n) {
        return impact.of(n);
    }

    /** Alias for {@link #impactOf} — the blast radius of a change to {@code n}. */
    public Impact blastRadius(NodeId n) {
        return impact.of(n);
    }

    public Influence influence(NodeId n) {
        return impact.influence(n);
    }

    public List<GraphNode> criticalPath(NodeId n) {
        return dependency.criticalPath(n);
    }

    // ---- Explanation / reasoning ----

    public Explanation explain(NodeId n) {
        return explanation.explain(n);
    }

    public Explanation whyApproved(NodeId decision) {
        return explanation.whyDecided(decision);
    }

    public Explanation explain(NodeId n, Lens lens) {
        return explanation.explain(n, lens);
    }

    /** The evidence (Observations and primary Artifacts) reachable from a claim. */
    public List<GraphNode> evidenceFor(NodeId claim) {
        return traversal.closure(claim, com.broksforge.fkge.query.Lenses.EVIDENCE.families(),
                        com.broksforge.fkge.query.Direction.OUT, -1)
                .others().stream()
                .filter(g -> g.kind() == Kind.OBSERVATION || g.kind() == Kind.ARTIFACT)
                .toList();
    }

    public CausalTrace rootCause(NodeId incident) {
        return reasoning.rootCause(incident);
    }

    public ConfidenceResult confidenceOf(NodeId n) {
        return reasoning.confidenceOf(n);
    }

    // ---- Traversal / search / projection ----

    public Neighborhood neighborhood(NodeId n, int depth) {
        return traversal.neighborhood(n, depth);
    }

    public Optional<Path> trace(NodeId from, NodeId to, Lens lens) {
        return traversal.path(from, to, lens.families(), lens.direction());
    }

    public boolean reachable(NodeId from, NodeId to, Lens lens) {
        return traversal.path(from, to, lens.families(), lens.direction()).isPresent();
    }

    public List<GraphNode> similarTo(NodeId n) {
        return search.similarTo(n);
    }

    public List<Pattern> patterns(int minCount) {
        return search.patterns(minCount);
    }

    public Summary summarize(NodeId n) {
        return projection.summarize(n);
    }

    // ---- Version comparison (delegated to FVCS; requires a repository) ----

    /** What changed between two commits — delegated to FVCS's diff, never reinvented. */
    public ChangeSet whatChanged(CommitRef from, CommitRef to) {
        if (repository == null) {
            throw new IllegalStateException("version comparison requires opening the engine over a Repository");
        }
        return repository.diff(from, to);
    }

    // ---- Escape hatches ----

    public GraphIndex index() {
        return index;
    }

    public LensRegistry lenses() {
        return lenses;
    }

    public Optional<Repository> repository() {
        return Optional.ofNullable(repository);
    }
}

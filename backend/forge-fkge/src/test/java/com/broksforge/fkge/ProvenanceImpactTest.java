package com.broksforge.fkge;

import com.broksforge.fkge.depend.DependencySet;
import com.broksforge.fkge.impact.Impact;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.provenance.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Provenance, dependency, and impact — the duality law, the reproducibility certificate, critical path. */
class ProvenanceImpactTest {

    @Test
    @DisplayName("provenance of the agent reaches model, provider, and prompt; certificate is its own hash")
    void provenanceReachesAncestors() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        Provenance p = fkge.provenanceOf(s.agent);
        assertTrue(p.contains(s.model));
        assertTrue(p.contains(s.provider));
        assertTrue(p.contains(s.prompt));
        assertEquals(fkge.index().node(s.agent).orElseThrow().hash(), p.certificate());
    }

    @Test
    @DisplayName("dependency is the reproduction-bearing subset of provenance: excludes evidence/observations")
    void dependencySubsetOfProvenance() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        DependencySet deps = fkge.dependenciesOf(s.deployment);
        Provenance prov = fkge.provenanceOf(s.deployment);

        List<?> depIds = deps.nodes().stream().map(GraphNode::id).toList();
        // every dependency is also in provenance
        assertTrue(deps.nodes().stream().allMatch(d -> prov.contains(d.id())));
        // the run (an observation cited as evidence) is provenance but NOT a rebuild dependency
        assertTrue(prov.contains(s.run));
        assertFalse(depIds.contains(s.run));
    }

    @Test
    @DisplayName("duality law: X in impact(N) iff N in provenance(X)")
    void dualityLaw() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);

        Impact provImpact = fkge.impactOf(s.provider);
        boolean deploymentInImpactOfProvider = provImpact.dependents().stream().anyMatch(g -> g.id().equals(s.deployment));
        boolean providerInProvenanceOfDeployment = fkge.provenanceOf(s.deployment).contains(s.provider);
        assertTrue(deploymentInImpactOfProvider);
        assertTrue(providerInProvenanceOfDeployment);
        assertEquals(providerInProvenanceOfDeployment, deploymentInImpactOfProvider);
    }

    @Test
    @DisplayName("blast radius of the model reaches the deployment; causality is not a dependency path")
    void blastRadius() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        Impact im = fkge.blastRadius(s.model);
        assertTrue(im.dependents().stream().anyMatch(g -> g.id().equals(s.agent)));
        assertTrue(im.dependents().stream().anyMatch(g -> g.id().equals(s.deployment)));
        // the incident is reached only by a causal edge, which impact does not follow
        assertFalse(im.dependents().stream().anyMatch(g -> g.id().equals(s.incident)));
    }

    @Test
    @DisplayName("critical dependency path of the agent is agent -> model -> provider")
    void criticalPath() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        List<GraphNode> path = fkge.criticalPath(s.agent);
        assertEquals(3, path.size());
        assertEquals(s.agent, path.get(0).id());
        assertEquals(s.provider, path.get(path.size() - 1).id());
    }
}

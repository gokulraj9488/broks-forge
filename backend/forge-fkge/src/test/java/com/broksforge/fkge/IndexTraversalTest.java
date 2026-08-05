package com.broksforge.fkge;

import com.broksforge.fkge.index.GraphIndex;
import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lenses;
import com.broksforge.fkge.traverse.Path;
import com.broksforge.fkge.traverse.Reach;
import com.broksforge.fkge.traverse.TraversalEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The index is a faithful, deterministic projection; the three traversal atoms behave as specified. */
class IndexTraversalTest {

    @Test
    @DisplayName("the index folds every continuant of the scenario, resolvable by id")
    void indexEnumeratesNodes() {
        TestSupport.Scenario s = TestSupport.scenario();
        GraphIndex index = GraphIndex.of(s.repo);
        assertEquals(10, index.nodes().size());
        assertTrue(index.node(s.agent).isPresent());
        assertTrue(index.node(s.incident).isPresent());
    }

    @Test
    @DisplayName("folding the same log twice yields an identical projection (deterministic)")
    void deterministicFold() {
        TestSupport.Scenario s = TestSupport.scenario();
        GraphIndex a = GraphIndex.of(s.repo);
        GraphIndex b = GraphIndex.of(s.repo);
        assertEquals(a.nodes(), b.nodes());
        assertEquals(a.out(s.agent), b.out(s.agent));
        assertEquals(a.in(s.provider), b.in(s.provider));
    }

    @Test
    @DisplayName("neighbors are typed and directional: agent uses model and prompt (composition, OUT)")
    void neighborsTyped() {
        TestSupport.Scenario s = TestSupport.scenario();
        TraversalEngine t = new TraversalEngine(GraphIndex.of(s.repo));
        var out = t.neighbors(s.agent, Lenses.COMPOSITION.families(), Direction.OUT);
        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(e -> e.to().equals(s.model)));
        assertTrue(out.stream().anyMatch(e -> e.to().equals(s.prompt)));
    }

    @Test
    @DisplayName("closure reaches transitive ancestors; a bound truncates it")
    void closureBounded() {
        TestSupport.Scenario s = TestSupport.scenario();
        TraversalEngine t = new TraversalEngine(GraphIndex.of(s.repo));
        Reach full = t.closure(s.agent, Lenses.DEPENDENCY.families(), Direction.OUT, -1);
        assertTrue(full.reached(s.provider), "provider is a transitive dependency of agent");

        Reach depth1 = t.closure(s.agent, Lenses.DEPENDENCY.families(), Direction.OUT, 1);
        assertTrue(depth1.reached(s.model), "model is one hop away");
        assertFalse(depth1.reached(s.provider), "provider is two hops away and beyond the bound");
    }

    @Test
    @DisplayName("path exhibits a connecting walk, or proves none exists")
    void pathExhibitsWalk() {
        TestSupport.Scenario s = TestSupport.scenario();
        TraversalEngine t = new TraversalEngine(GraphIndex.of(s.repo));
        Optional<Path> walk = t.path(s.agent, s.provider, Lenses.DEPENDENCY.families(), Direction.OUT);
        assertTrue(walk.isPresent());
        assertEquals(2, walk.get().length(), "agent -> model -> provider");

        Optional<Path> none = t.path(s.provider, s.agent, Lenses.DEPENDENCY.families(), Direction.OUT);
        assertTrue(none.isEmpty(), "no upstream walk from provider to agent");
    }
}

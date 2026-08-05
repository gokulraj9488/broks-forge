package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.op.Delta;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** closure (op), traverse (op), and diff (op). */
class ClosureTraverseDiffTest {

    private Address.Revision create(ForgeKernel k, Revision r) {
        return (Address.Revision) k.append(Fixtures.ORG, new AppendCommand.CreateNode(r), Fixtures.ACTOR)
                .address().orElseThrow();
    }

    @Test
    @DisplayName("closure gathers the transitive composition members")
    void closure() {
        ForgeKernel k = Fixtures.kernel();
        Revision p1 = Fixtures.prompt("one");
        Revision p2 = Fixtures.prompt("two");
        create(k, p1);
        create(k, p2);
        Revision agent = Fixtures.artifact("agent", CanonicalValue.NULL, List.of(
                new com.broksforge.kernel.api.Ref(new Verb("uses", EdgeFamily.COMPOSITION), p1.hash()),
                new com.broksforge.kernel.api.Ref(new Verb("uses", EdgeFamily.COMPOSITION), p2.hash())));
        create(k, agent);

        var members = k.closure(agent.hash());
        assertEquals(3, members.size());
        assertTrue(members.containsKey(agent.hash()));
        assertTrue(members.containsKey(p1.hash()));
        assertTrue(members.containsKey(p2.hash()));
    }

    @Test
    @DisplayName("traverse follows intrinsic composition edges from a revision")
    void traverseIntrinsic() {
        ForgeKernel k = Fixtures.kernel();
        Revision p1 = Fixtures.prompt("one");
        Revision p2 = Fixtures.prompt("two");
        create(k, p1);
        create(k, p2);
        Revision agentRev = Fixtures.artifact("agent", CanonicalValue.NULL, List.of(
                new com.broksforge.kernel.api.Ref(new Verb("uses", EdgeFamily.COMPOSITION), p1.hash()),
                new com.broksforge.kernel.api.Ref(new Verb("uses", EdgeFamily.COMPOSITION), p2.hash())));
        Address.Revision agent = create(k, agentRev);

        Subgraph g = k.traverse(Fixtures.ORG, new Query(agent,
                java.util.Set.of(EdgeFamily.COMPOSITION), Query.Direction.OUT, 3));
        assertEquals(2, g.edges().size());
        assertEquals(3, g.nodes().size()); // agent + two components
    }

    @Test
    @DisplayName("traverse follows extrinsic edges and respects retraction")
    void traverseExtrinsic() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision a = create(k, Fixtures.prompt("a"));
        Address.Revision b = create(k, Fixtures.prompt("b"));
        Address.Node an = new Address.Node(a.org(), a.kind(), a.node());
        Address.Node bn = new Address.Node(b.org(), b.kind(), b.node());
        EdgeKey edge = new EdgeKey(an, new Verb("caused", EdgeFamily.CAUSALITY), bn);

        k.append(Fixtures.ORG, new AppendCommand.AssertEdge(edge), Fixtures.ACTOR);
        Subgraph before = k.traverse(Fixtures.ORG, new Query(an,
                java.util.Set.of(EdgeFamily.CAUSALITY), Query.Direction.OUT, 2));
        assertEquals(1, before.edges().size());

        k.append(Fixtures.ORG, new AppendCommand.RetractEdge(edge), Fixtures.ACTOR);
        Subgraph after = k.traverse(Fixtures.ORG, new Query(an,
                java.util.Set.of(EdgeFamily.CAUSALITY), Query.Direction.OUT, 2));
        assertTrue(after.edges().isEmpty());
    }

    @Test
    @DisplayName("diff is empty for identical content and located for a changed field")
    void diff() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision a = create(k, Fixtures.prompt("hello"));
        Address.Revision b = create(k, Fixtures.prompt("world"));

        assertTrue(k.diff(a.revision(), a.revision()).identical());

        Delta delta = k.diff(a.revision(), b.revision());
        assertFalse(delta.identical());
        assertTrue(delta.changes().stream().anyMatch(c -> c.path().equals("/payload/text")));
    }
}

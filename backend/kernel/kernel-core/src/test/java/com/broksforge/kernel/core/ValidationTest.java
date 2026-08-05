package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.validate.IntegrityReport;
import com.broksforge.kernel.core.validate.IntegrityScanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The validation layer: a well-formed graph scans clean across every check. */
class ValidationTest {

    private Address.Revision create(ForgeKernel k, Revision r) {
        return (Address.Revision) k.append(Fixtures.ORG, new AppendCommand.CreateNode(r), Fixtures.ACTOR)
                .address().orElseThrow();
    }

    @Test
    @DisplayName("a healthy graph (revisions, refs, names, edges) scans clean")
    void healthyScansClean() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision p1 = create(k, Fixtures.prompt("one"));
        Address.Revision p2 = create(k, Fixtures.prompt("two"));
        Revision agent = Fixtures.artifact("agent", CanonicalValue.NULL, List.of(
                Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), p1.revision()),
                Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), p2.revision())));
        Address.Revision agentAddr = create(k, agent);
        k.append(Fixtures.ORG, new AppendCommand.RepointName(Name.of("prod"), agentAddr, null), Fixtures.ACTOR);
        k.append(Fixtures.ORG, new AppendCommand.AssertEdge(new EdgeKey(
                p1, new Verb("caused", EdgeFamily.CAUSALITY), p2)), Fixtures.ACTOR);

        IntegrityReport report = new IntegrityScanner().scan(k, Fixtures.ORG);
        assertTrue(report.clean(), () -> "expected clean, got: " + report.findings());
        assertEquals(0, report.errorCount());
    }

    @Test
    @DisplayName("an empty org scans clean")
    void emptyScansClean() {
        ForgeKernel k = Fixtures.kernel();
        assertTrue(new IntegrityScanner().scan(k, Fixtures.ORG).clean());
    }
}

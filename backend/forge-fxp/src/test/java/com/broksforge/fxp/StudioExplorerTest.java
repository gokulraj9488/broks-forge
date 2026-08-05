package com.broksforge.fxp;

import com.broksforge.fkge.provenance.Provenance;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.SnapshotRef;
import com.broksforge.fxp.explore.ExplorerService;
import com.broksforge.fxp.studio.StudioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Studio authors lawful facts and versions; Explorer reads them back through FKGE. */
class StudioExplorerTest {

    @Test
    @DisplayName("Studio-authored claim carries confidence; Explorer propagates the weakest-link bound")
    void authoringAndConfidence() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        ExplorerService explorer = s.client.explorer();
        ConfidenceResult c = explorer.confidence(s.deployment.node());
        assertTrue(c.defined());
        assertEquals(0, c.confidence().compareTo(new BigDecimal("0.60")), "min(0.90, 0.60)");
        assertEquals(s.benchmark.node(), c.weakestLink());
    }

    @Test
    @DisplayName("Explorer provenance of the agent reaches its provider, certified")
    void provenance() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        Provenance p = s.client.explorer().provenance(s.agent.node());
        assertTrue(p.contains(s.provider.node()));
        assertEquals(s.agent.hash(), p.certificate());
    }

    @Test
    @DisplayName("Studio versioning: a commit is recorded and appears in branch history")
    void versioning() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        StudioService studio = s.client.studio();
        Branch main = studio.branch("main");
        SnapshotRef snap = studio.snapshot("v1", List.of(s.provider, s.model, s.agent, s.prompt));
        CommitRef c1 = studio.commit(main, snap, "initial support-bot");
        assertEquals(1, studio.history(main).size());
        assertEquals("initial support-bot", c1.message());
    }
}

package com.broksforge.fkge;

import com.broksforge.fkge.query.Direction;
import com.broksforge.fkge.query.Lens;
import com.broksforge.kernel.api.EdgeFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The extension SPI adds engineering questions additively, without touching the engine core. */
class SpiTest {

    @Test
    @DisplayName("a LensModule contributes a custom lens usable for reasoning")
    void customLensViaSpi() {
        TestSupport.Scenario s = TestSupport.scenario();
        Lens compositionOnly = Lens.of("composition-only", Direction.OUT, EdgeFamily.COMPOSITION);

        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo,
                registry -> registry.register(compositionOnly));

        assertTrue(fkge.lenses().lens("composition-only").isPresent());
        // built-ins remain registered alongside the contributed lens
        assertTrue(fkge.lenses().lens("provenance").isPresent());

        Optional<Lens> lens = fkge.lenses().lens("composition-only");
        assertEquals(true, fkge.reachable(s.agent, s.provider, lens.orElseThrow()),
                "agent reaches provider along composition only");
    }
}

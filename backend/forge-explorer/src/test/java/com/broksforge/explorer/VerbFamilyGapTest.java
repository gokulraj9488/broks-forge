package com.broksforge.explorer;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression test for <b>KAP-2 (REJECTED)</b>: verb→family pairing is intentionally <em>not</em>
 * enforced by the kernel.
 *
 * <p>Phase 1.5 raised KAP-2 because the {@link Verb} javadoc claimed a name→family "registry that lives
 * in the kernel core." The Kernel Amendment Review rejected kernel enforcement — a kernel-maintained
 * registry would add mutable state outside the log and narrow the "open verbs" half of Article III;
 * each {@link Verb} already belongs to exactly one family, which satisfies Article III per assertion.
 * The {@code Verb} javadoc was corrected to say so. Keeping one verb name meaning one family everywhere
 * is therefore a userspace convention, which this application implements with its {@link Verbs} catalog.
 *
 * <p>These tests document the accepted, intended behavior.
 */
class VerbFamilyGapTest {

    @Test
    @DisplayName("BY DESIGN: a verb name may pair with any family; consistency is a userspace concern")
    void sameVerbNameDifferentFamilies() {
        Verb causedAsCausality = new Verb("caused", EdgeFamily.CAUSALITY);
        Verb causedAsComposition = new Verb("caused", EdgeFamily.COMPOSITION); // semantically wrong, still legal
        assertNotEquals(causedAsCausality.family(), causedAsComposition.family());

        ForgeExplorer forge = TestKernel.explorer();
        Handle a = forge.createArtifact("prompt", CanonicalValue.objectBuilder().put("text", "a").build());
        Handle b = forge.createArtifact("agent", CanonicalValue.objectBuilder().put("text", "b").build());
        // The kernel accepts an edge whose verb is filed under the wrong family.
        assertDoesNotThrow(() -> forge.assertEdge(b.address(), causedAsComposition, a.address()));
    }

    @Test
    @DisplayName("COMPENSATION: the Explorer's catalog pins each verb name to one family")
    void userspaceCatalogIsConsistent() {
        assertEquals(EdgeFamily.CAUSALITY, Verbs.byName("caused").orElseThrow().family());
        assertEquals(EdgeFamily.EVIDENCE, Verbs.byName("cites").orElseThrow().family());
        assertEquals(EdgeFamily.COMPOSITION, Verbs.byName("uses").orElseThrow().family());
    }
}

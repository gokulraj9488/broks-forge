package com.broksforge.fvcs;

import com.broksforge.kernel.api.Kind;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.fvcs.ontology.FvcsOntology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FVCS composes additively onto the frozen ontology via the public SPI — no foundation change. */
class OntologyCompositionTest {

    private final Ontology composed = FvcsOntology.composed();

    @Test
    @DisplayName("the composed ontology builds and contains both frozen and FVCS types")
    void composes() {
        // frozen base still present
        assertTrue(composed.type("Agent").isPresent());
        assertTrue(composed.type("ArtifactPackage").isPresent());
        // FVCS additions
        assertEquals(Kind.DECISION, composed.type("Commit").orElseThrow().kind());
        assertEquals(Kind.DECISION, composed.type("Tag").orElseThrow().kind());
        assertEquals(Kind.CLAIM, composed.type("CompatibilityVerdict").orElseThrow().kind());
    }

    @Test
    @DisplayName("a commit records exactly one snapshot (ArtifactPackage), and tags mark commits")
    void relationsRegistered() {
        var records = composed.match(com.broksforge.fvcs.ontology.FvcsVerbs.RECORDS,
                composed.type("Commit").orElseThrow(), composed.type("ArtifactPackage").orElseThrow());
        assertTrue(records.isPresent());
        assertEquals(com.broksforge.knowledge.ontology.Cardinality.EXACTLY_ONE, records.get().cardinality());

        var marks = composed.match(com.broksforge.fvcs.ontology.FvcsVerbs.MARKS,
                composed.type("Tag").orElseThrow(), composed.type("Commit").orElseThrow());
        assertTrue(marks.isPresent());
    }

    @Test
    @DisplayName("snapshot is the frozen ArtifactPackage — no new snapshot type was introduced")
    void snapshotIsArtifactPackage() {
        assertEquals("artifact-package", ObjectTypes.ARTIFACT_PACKAGE.subtype());
        assertTrue(composed.type("Snapshot").isEmpty());
    }
}

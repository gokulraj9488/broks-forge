package com.broksforge.fvcs.ontology;

import com.broksforge.knowledge.ontology.Cardinality;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Ontologies;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.ontology.RelationType;
import com.broksforge.knowledge.spi.OntologyModule;

/**
 * Composes the FVCS versioning vocabulary onto the frozen Forge ontology, using the knowledge system's
 * public {@link OntologyModule} SPI. This is additive composition, not modification: the frozen
 * {@code Ontologies.forge()} and the {@code forge-knowledge} code are untouched; FVCS builds a new
 * ontology = the canonical base ⊕ the FVCS module.
 */
public final class FvcsOntology {

    private FvcsOntology() {
    }

    /** @return the FVCS additions as a composable module */
    public static OntologyModule module() {
        return FvcsOntology::contribute;
    }

    /** @return a consistent ontology combining the frozen canonical types with the FVCS additions */
    public static Ontology composed() {
        Ontology.Builder b = Ontology.builder();
        Ontologies.canonicalModule().contribute(b);   // frozen base, unchanged
        contribute(b);                                  // FVCS additions
        return b.build();
    }

    private static void contribute(Ontology.Builder b) {
        b.object(FvcsTypes.COMMIT);
        b.object(FvcsTypes.TAG);
        b.object(FvcsTypes.COMPATIBILITY_VERDICT);

        // Commit DAG: a commit derives from its parent commit(s) (a merge has ≥2).
        b.relation(RelationType.verb(FvcsVerbs.PARENT)
                .from(FvcsTypes.COMMIT.name()).to(FvcsTypes.COMMIT.name())
                .card(Cardinality.ANY).build());
        // Commit → the snapshot (tree) it records.
        b.relation(RelationType.verb(FvcsVerbs.RECORDS)
                .from(FvcsTypes.COMMIT.name()).to(ObjectTypes.ARTIFACT_PACKAGE.name())
                .card(Cardinality.EXACTLY_ONE).build());
        // Tag → the commit it names.
        b.relation(RelationType.verb(FvcsVerbs.MARKS)
                .from(FvcsTypes.TAG.name()).to(FvcsTypes.COMMIT.name())
                .card(Cardinality.EXACTLY_ONE).build());
        // A CompatibilityVerdict (a Claim) cites the runs that evidence it via the frozen `cites`
        // relation (Claim → Observation) — no new evidence verb is needed.
    }
}

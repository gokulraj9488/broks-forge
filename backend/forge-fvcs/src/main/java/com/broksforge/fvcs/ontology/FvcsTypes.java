package com.broksforge.fvcs.ontology;

import com.broksforge.kernel.api.Kind;
import com.broksforge.knowledge.ontology.FieldType;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.PayloadSchema;

/**
 * The three additive knowledge object types FVCS introduces, each justified from first principles
 * (see THEORY §16):
 *
 * <ul>
 *   <li>{@link #COMMIT} — an act of will to checkpoint a snapshot; a {@code Decision} (epistemic typing
 *       forbids conflating a commit with the snapshot Artifact it records).</li>
 *   <li>{@link #TAG} — the act of immovably naming a commit; a {@code Decision}, with a {@code role}
 *       ({@code lightweight}/{@code release}/{@code baseline}) that folds Release and Baseline in as
 *       roles rather than new types (KN-0002).</li>
 *   <li>{@link #COMPATIBILITY_VERDICT} — "B may replace A"; an evidenced {@code Claim} (Law 5 forbids a
 *       naked compatibility flag).</li>
 * </ul>
 *
 * A snapshot is <em>not</em> a new type — it is the frozen {@code ArtifactPackage}.
 */
public final class FvcsTypes {

    private FvcsTypes() {
    }

    /** Tag roles (payload values, not subtypes). */
    public static final String ROLE_LIGHTWEIGHT = "lightweight";
    public static final String ROLE_RELEASE = "release";
    public static final String ROLE_BASELINE = "baseline";

    public static final ObjectType COMMIT = ObjectType.of("Commit", Kind.DECISION, "commit",
            PayloadSchema.builder()
                    .required("message", FieldType.STRING)
                    .optional("branch", FieldType.STRING)
                    .optional("snapshot", FieldType.STRING)
                    .optional("judgment-call", FieldType.BOOL)
                    .build());

    public static final ObjectType TAG = ObjectType.of("Tag", Kind.DECISION, "tag",
            PayloadSchema.builder()
                    .required("name", FieldType.STRING)
                    .optional("message", FieldType.STRING)
                    .roles(ROLE_LIGHTWEIGHT, ROLE_RELEASE, ROLE_BASELINE)
                    .build());

    public static final ObjectType COMPATIBILITY_VERDICT = ObjectType.of(
            "CompatibilityVerdict", Kind.CLAIM, "compatibility-verdict",
            PayloadSchema.builder()
                    .required("statement", FieldType.STRING)
                    .required("method", FieldType.STRING)
                    .required("confidence", FieldType.NUMBER)
                    .optional("from", FieldType.STRING)
                    .optional("to", FieldType.STRING)
                    .build());
}

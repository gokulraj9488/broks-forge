package com.broksforge.knowledge.validate;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.PayloadCheck;
import com.broksforge.knowledge.ontology.PayloadField;
import com.broksforge.knowledge.ontology.RelationType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The semantic-layer enforcement point (KN-0004). Given the ontology, it validates a knowledge object's
 * payload and its intrinsic relationships — and an extrinsic relationship — against the object/relation
 * schemas and the cross-object invariants, <em>before</em> anything is appended. It never weakens a
 * kernel law; it only adds stricter, type-level rules the kernel deliberately does not know.
 */
public final class KnowledgeValidator {

    private final Ontology ontology;

    /** @param ontology the ontology to validate against */
    public KnowledgeValidator(Ontology ontology) {
        this.ontology = ontology;
    }

    /**
     * Validates a new object (or new revision) of {@code subject} with the given payload and intrinsic
     * links.
     *
     * @param subject the object type being created
     * @param payload the payload
     * @param links   the intrinsic relationships declared at creation
     * @return the validation result
     */
    public ValidationResult validateObject(ObjectType subject, CanonicalValue payload, List<LinkSpec> links) {
        List<ValidationIssue> issues = new ArrayList<>();
        checkPayload(subject, payload, issues);
        checkLinks(subject, links, issues);
        return new ValidationResult(issues);
    }

    /**
     * Validates a single extrinsic relationship {@code from --verb--> to}.
     *
     * @param from the source object type
     * @param verb the verb
     * @param to   the target object type
     * @return the validation result
     */
    public ValidationResult validateRelation(ObjectType from, Verb verb, ObjectType to) {
        Optional<RelationType> match = ontology.match(verb, from, to);
        if (match.isEmpty()) {
            return new ValidationResult(List.of(ValidationIssue.error("ENDPOINT_TYPE",
                    "no relation '" + verb.name() + "' from " + from.name() + " to " + to.name())));
        }
        if (match.get().intrinsic()) {
            return new ValidationResult(List.of(ValidationIssue.error("INTRINSIC_AS_EXTRINSIC",
                    "relation '" + verb.name() + "' " + from.name() + "→" + to.name()
                            + " is intrinsic; declare it at object creation, not as an asserted edge")));
        }
        return ValidationResult.ok();
    }

    // ---- payload -----------------------------------------------------------------------------

    private void checkPayload(ObjectType subject, CanonicalValue payload, List<ValidationIssue> issues) {
        List<PayloadField> required = subject.schema().required();
        if (!required.isEmpty() && !(payload instanceof CanonicalValue.Obj)) {
            issues.add(ValidationIssue.error("PAYLOAD_SHAPE",
                    subject.name() + " payload must be an object with " + required.size() + " required field(s)"));
            return;
        }
        if (payload instanceof CanonicalValue.Obj obj) {
            Map<String, CanonicalValue> entries = obj.entries();
            for (PayloadField f : subject.schema().fields()) {
                CanonicalValue v = entries.get(f.key());
                if (v == null) {
                    if (f.required()) {
                        issues.add(ValidationIssue.error("MISSING_FIELD",
                                subject.name() + " requires field '" + f.key() + "' (" + f.type() + ")"));
                    }
                } else if (!f.type().matches(v)) {
                    issues.add(ValidationIssue.error("FIELD_TYPE",
                            subject.name() + " field '" + f.key() + "' must be " + f.type()));
                }
            }
            CanonicalValue role = entries.get("role");
            if (role instanceof CanonicalValue.Str s && !subject.schema().roles().isEmpty()
                    && !subject.schema().roles().contains(s.value())) {
                issues.add(ValidationIssue.error("ROLE",
                        subject.name() + " role '" + s.value() + "' not in " + subject.schema().roles()));
            }
        }
        Optional<PayloadCheck> custom = ontology.checkFor(subject.name());
        if (custom.isPresent()) {
            for (String problem : custom.get().check(payload)) {
                issues.add(ValidationIssue.error("CUSTOM", subject.name() + ": " + problem));
            }
        }
    }

    // ---- relationships -----------------------------------------------------------------------

    private void checkLinks(ObjectType subject, List<LinkSpec> links, List<ValidationIssue> issues) {
        Map<RelationType, Integer> counts = new LinkedHashMap<>();
        boolean hasEvidence = false;

        for (LinkSpec link : links) {
            Optional<RelationType> match = ontology.match(link.verb(), subject, link.targetType());
            if (match.isEmpty()) {
                issues.add(ValidationIssue.error("ENDPOINT_TYPE",
                        "no relation '" + link.verb().name() + "' from " + subject.name()
                                + " to " + link.targetType().name()));
                continue;
            }
            RelationType r = match.get();
            if (!r.intrinsic()) {
                issues.add(ValidationIssue.error("EXTRINSIC_AT_CREATION",
                        "relation '" + link.verb().name() + "' is extrinsic; assert it after creation"));
                continue;
            }
            counts.merge(r, 1, Integer::sum);
            if (r.family() == EdgeFamily.EVIDENCE) {
                hasEvidence = true;
            }
        }

        // Max cardinality on the relations that were used.
        for (Map.Entry<RelationType, Integer> e : counts.entrySet()) {
            if (!e.getKey().cardinality().allows(e.getValue())) {
                issues.add(ValidationIssue.error("CARDINALITY",
                        subject.name() + " has " + e.getValue() + " '" + e.getKey().verb().name()
                                + "' edge(s); cardinality is " + e.getKey().cardinality()));
            }
        }
        // Min cardinality on required relations that may be entirely absent.
        for (RelationType req : ontology.requiredRelationsFrom(subject)) {
            int n = counts.getOrDefault(req, 0);
            if (n < req.cardinality().min()) {
                issues.add(ValidationIssue.error("MISSING_RELATION",
                        subject.name() + " requires " + req.cardinality() + " '" + req.verb().name()
                                + "' → " + endpointLabel(req)));
            }
        }
        // CI-2: a Claim must carry at least one evidence-family relation (the Claim law, at the
        // semantic layer — the kernel enforces the same, this gives an earlier, typed message).
        if (subject.kind() == com.broksforge.kernel.api.Kind.CLAIM && !hasEvidence) {
            issues.add(ValidationIssue.error("CLAIM_EVIDENCE",
                    subject.name() + " (a claim) must cite at least one evidence reference"));
        }
    }

    private static String endpointLabel(RelationType r) {
        if (r.toType() != null) {
            return r.toType();
        }
        if (!r.toTypes().isEmpty()) {
            return String.join("|", r.toTypes());
        }
        return r.toKind() != null ? r.toKind().wireName() : "?";
    }
}

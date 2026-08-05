package com.broksforge.knowledge.graph;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.command.AppendResult;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.validate.KnowledgeValidator;
import com.broksforge.knowledge.validate.LinkSpec;
import com.broksforge.knowledge.validate.ValidationIssue;
import com.broksforge.knowledge.validate.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The typed, ontology-aware façade over a single organization's {@link ForgeKernel} — the primary write
 * surface of the Knowledge System.
 *
 * <p>Every write is validated against the ontology <em>before</em> it touches the append-only log
 * (KN-0004): {@link #define} builds a kernel {@code CreateNode} whose intrinsic links become
 * {@code Ref}s; {@link #addRevision} versions a revisable object (and refuses reality/decision objects,
 * supplying the KAP-3 discipline in userspace); {@link #relate} asserts an extrinsic edge; {@link #deploy}
 * records a Deployment decision and repoints a kernel name. It adds no capability the kernel lacks and
 * uses only the public kernel API.
 */
public final class KnowledgeGraph {

    private final ForgeKernel kernel;
    private final OrgId org;
    private final ActorId actor;
    private final Ontology ontology;
    private final KnowledgeValidator validator;

    private KnowledgeGraph(ForgeKernel kernel, OrgId org, ActorId actor, Ontology ontology) {
        this.kernel = kernel;
        this.org = org;
        this.actor = actor;
        this.ontology = ontology;
        this.validator = new KnowledgeValidator(ontology);
    }

    /**
     * @param kernel   the kernel
     * @param org      the organization
     * @param actor    the signing actor
     * @param ontology the ontology to enforce
     * @return a façade bound to {@code org}/{@code actor}
     */
    public static KnowledgeGraph open(ForgeKernel kernel, OrgId org, ActorId actor, Ontology ontology) {
        if (kernel == null || org == null || actor == null || ontology == null) {
            throw new IllegalArgumentException("kernel, org, actor, and ontology must not be null");
        }
        return new KnowledgeGraph(kernel, org, actor, ontology);
    }

    /** @return the ontology being enforced */
    public Ontology ontology() {
        return ontology;
    }

    /** @return the underlying kernel */
    public ForgeKernel kernel() {
        return kernel;
    }

    /** @return the bound organization */
    public OrgId org() {
        return org;
    }

    /**
     * Creates a new knowledge object, validating payload and intrinsic links against the ontology first.
     *
     * @param type    the object type
     * @param payload the payload
     * @param links   intrinsic relationships (become kernel refs)
     * @return the created object
     * @throws com.broksforge.knowledge.validate.KnowledgeException if validation fails
     */
    public KnowledgeObject define(ObjectType type, CanonicalValue payload, Link... links) {
        List<Link> linkList = List.of(links);
        validator.validateObject(type, payload, specs(linkList)).throwIfInvalid();
        Revision revision = Revision.of(type.kind(), type.subtype(), payload, refs(type, linkList));
        AppendResult r = kernel.append(org, new AppendCommand.CreateNode(revision), actor);
        return new KnowledgeObject(type, (Address.Revision) r.address().orElseThrow(), revision);
    }

    /**
     * Adds a new revision to a revisable object (Artifacts/Claims). Refuses Observations/Decisions,
     * which are single-revision (CI-6) — the semantic layer supplies this discipline in userspace.
     *
     * @param object  the existing object
     * @param payload the new payload
     * @param links   intrinsic relationships for the new revision
     * @return the new revision handle
     */
    public KnowledgeObject addRevision(KnowledgeObject object, CanonicalValue payload, Link... links) {
        if (object.type().isSingleRevision()) {
            ValidationResult r = new ValidationResult(List.of(ValidationIssue.error("IMMUTABLE",
                    object.type().name() + " is a " + object.type().kind().wireName()
                            + " and is never revised (CI-6); record a new object instead")));
            r.throwIfInvalid();
        }
        List<Link> linkList = List.of(links);
        validator.validateObject(object.type(), payload, specs(linkList)).throwIfInvalid();
        Revision revision = Revision.of(object.type().kind(), object.type().subtype(), payload, refs(object.type(), linkList));
        AppendResult r = kernel.append(org, new AppendCommand.AddRevision(object.node(), revision), actor);
        return new KnowledgeObject(object.type(), (Address.Revision) r.address().orElseThrow(), revision);
    }

    /**
     * Asserts an extrinsic relationship between two existing objects (causality, post-hoc support).
     *
     * @param from the source object
     * @param verb the verb
     * @param to   the target object
     */
    public void relate(KnowledgeObject from, Verb verb, KnowledgeObject to) {
        validator.validateRelation(from.type(), verb, to.type()).throwIfInvalid();
        kernel.append(org, new AppendCommand.AssertEdge(new EdgeKey(from.address(), verb, to.address())), actor);
    }

    /**
     * Records a Deployment decision and repoints a kernel name at the deployed revision.
     *
     * @param name        the deployment name (e.g. {@code deploy/prod/support-agent})
     * @param deployment  the deployment object type (usually {@code ObjectTypes.DEPLOYMENT})
     * @param target      the Agent/Workflow/Package revision being deployed (intent: applied)
     * @param environment the target Environment (intent: targets)
     * @param restingOn   the claims the decision rests on (intent: rests_on); must be non-empty
     * @param statement   a human statement of the decision
     * @return the deployment decision object
     */
    public KnowledgeObject deploy(Name name, ObjectType deployment, KnowledgeObject target,
                                  KnowledgeObject environment, List<KnowledgeObject> restingOn, String statement) {
        List<Link> links = new ArrayList<>();
        links.add(Link.of(com.broksforge.knowledge.ontology.Verbs.APPLIED, target));
        links.add(Link.of(com.broksforge.knowledge.ontology.Verbs.TARGETS, environment));
        for (KnowledgeObject claim : restingOn) {
            links.add(Link.of(com.broksforge.knowledge.ontology.Verbs.RESTS_ON, claim));
        }
        CanonicalValue payload = CanonicalValue.objectBuilder().put("statement", statement).build();
        KnowledgeObject decision = define(deployment, payload, links.toArray(new Link[0]));
        // Repoint the deployment name at the deployed revision (CAS on the current pointer).
        Optional<Address.Revision> current = kernel.resolve(org, name);
        kernel.append(org, new AppendCommand.RepointName(name, target.address(), current.orElse(null)), actor);
        return decision;
    }

    /**
     * @param name a name
     * @return the revision the name resolves to, if any
     */
    public Optional<Address.Revision> resolve(Name name) {
        return kernel.resolve(org, name);
    }

    /** @return a fresh projection of this organization's knowledge graph */
    public KnowledgeView view() {
        return KnowledgeView.of(kernel, org, ontology);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<LinkSpec> specs(List<Link> links) {
        List<LinkSpec> specs = new ArrayList<>(links.size());
        for (Link l : links) {
            specs.add(new LinkSpec(l.verb(), l.target().type()));
        }
        return specs;
    }

    private List<Ref> refs(ObjectType subject, List<Link> links) {
        // Only intrinsic links become refs; validation has already rejected extrinsic ones here.
        List<Ref> refs = new ArrayList<>(links.size());
        for (Link l : links) {
            refs.add(Ref.of(l.verb(), l.target().hash()));
        }
        return refs;
    }
}

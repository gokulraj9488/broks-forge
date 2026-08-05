package com.broksforge.platform.projection;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Projects existing Broks Forge entities into Platform V2 Knowledge as <b>lawful</b> artifacts, consuming
 * only public Platform V2 APIs. Per the approved architecture, V1's opaque external "agents" are NOT
 * projected to V2 {@code Agent}s (which require a Prompt); instead configuration projects to
 * Provider/Model/Prompt/Dataset and execution truth to Evaluation/EvaluationVerdict — none of which
 * require an Agent, so no fabrication and no ontology amendment is needed.
 *
 * <p><b>Idempotent by construction:</b> each artifact is bound to a stable kernel {@link Name} derived from
 * its V1 id (the kernel's designed mechanism for stable pointers — "Names are the only mutable state").
 * Re-projecting resolves the Name and returns the existing artifact rather than duplicating it, and the
 * Name preserves the V1 → V2 identity mapping.
 */
@Service
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class KnowledgeProjectionService {

    public KnowledgeObject projectProvider(ForgeClient client, UUID providerId, String name) {
        return resolveOrCreate(client, Name.of("v1/provider/" + providerId),
                () -> client.studio().create(ObjectTypes.PROVIDER, obj("name", name)));
    }

    public KnowledgeObject projectModel(ForgeClient client, UUID providerId,
                                        KnowledgeObject providerArtifact, String modelId) {
        return resolveOrCreate(client, Name.of("v1/model/" + providerId + "/" + nameSegment(modelId)),
                () -> client.studio().create(ObjectTypes.MODEL, obj("model_id", modelId),
                        Link.of(Verbs.USES, providerArtifact)));
    }

    /**
     * Renders an arbitrary model identifier as a legal kernel name segment.
     *
     * <p>A kernel name segment admits only {@code [A-Za-z0-9][A-Za-z0-9._-]*}, but real model
     * identifiers do not respect that — {@code llama3.2:1b} and {@code gpt-4o@2024-08-06} are
     * ordinary names. Projecting them verbatim threw, and the backfill skipped the model entirely,
     * so a legitimately registered model was silently missing from the graph.
     *
     * <p>Illegal characters are replaced and a short digest of the original is appended, so the
     * mapping stays deterministic (the same model always resolves to the same pointer) and two
     * models that differ only in illegal characters cannot collide. The untouched identifier is
     * still stored as the object's {@code model_id} property, which is what the product displays —
     * this only sanitises the internal pointer.
     */
    static String nameSegment(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "unnamed";
        }
        String cleaned = modelId.replaceAll("[^A-Za-z0-9._-]", "-");
        if (cleaned.isEmpty() || !Character.isLetterOrDigit(cleaned.charAt(0))) {
            cleaned = "m" + cleaned;
        }
        if (cleaned.equals(modelId)) {
            return cleaned; // already legal — leave existing pointers exactly as they were
        }
        String digest = Integer.toHexString(modelId.hashCode() & 0x7fffffff);
        String prefix = cleaned.length() > 96 ? cleaned.substring(0, 96) : cleaned;
        return prefix + "-" + digest;
    }

    public KnowledgeObject projectPrompt(ForgeClient client, UUID promptId, String text) {
        return resolveOrCreate(client, Name.of("v1/prompt/" + promptId),
                () -> client.studio().create(ObjectTypes.PROMPT, obj("text", text)));
    }

    public KnowledgeObject projectDataset(ForgeClient client, UUID datasetId, String contentHash) {
        return resolveOrCreate(client, Name.of("v1/dataset/" + datasetId),
                () -> client.studio().create(ObjectTypes.DATASET, obj("content_hash", contentHash)));
    }

    /** An Evaluation artifact: {@code metrics} + {@code uses → Dataset} (≥1). No Agent required. */
    public KnowledgeObject projectEvaluation(ForgeClient client, UUID evaluationId,
                                             List<String> metrics, List<KnowledgeObject> datasets) {
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("an Evaluation must use at least one Dataset");
        }
        return resolveOrCreate(client, Name.of("v1/evaluation/" + evaluationId), () -> {
            Link[] links = datasets.stream().map(d -> Link.of(Verbs.USES, d)).toArray(Link[]::new);
            List<CanonicalValue> metricValues = metrics.stream()
                    .map(m -> (CanonicalValue) CanonicalValue.of(m)).toList();
            CanonicalValue payload = CanonicalValue.objectBuilder()
                    .put("metrics", CanonicalValue.array(metricValues))
                    .build();
            return client.studio().create(ObjectTypes.EVALUATION, payload, links);
        });
    }

    /**
     * An EvaluationVerdict claim whose evidence is {@code measured_by → Evaluation}. This satisfies the
     * kernel's Claim Law (≥1 evidence-family reference) with no Observation, Run, or Agent — the lawful way
     * to record a scored result of an evaluation.
     */
    public KnowledgeObject projectVerdict(ForgeClient client, String verdictKey, String statement,
                                          String method, BigDecimal confidence, KnowledgeObject evaluation) {
        return resolveOrCreate(client, Name.of("v1/verdict/" + verdictKey),
                () -> client.studio().authorClaim(ObjectTypes.EVALUATION_VERDICT, statement, method, confidence,
                        Link.of(Verbs.MEASURED_BY, evaluation)));
    }

    /**
     * Resolve the artifact bound to {@code name} (idempotent hit) or create it and bind the name via a CAS
     * repoint. Uses only public kernel APIs (resolve + append(RepointName)).
     */
    private KnowledgeObject resolveOrCreate(ForgeClient client, Name name, Supplier<KnowledgeObject> creator) {
        ForgeKernel kernel = client.repository().kernel();
        var org = client.repository().org();

        Optional<KnowledgeObject> existing = load(client, kernel.resolve(org, name).orElse(null));
        if (existing.isPresent()) {
            return existing.get();
        }
        KnowledgeObject created = creator.get();
        try {
            kernel.append(org, new AppendCommand.RepointName(name, created.address(), null), client.actor());
        } catch (KernelException e) {
            if (e.reason() == KernelException.Reason.CAS_FAILURE) {
                // Lost a concurrent race — return the winner the name now points to.
                Optional<KnowledgeObject> winner = load(client, kernel.resolve(org, name).orElse(null));
                if (winner.isPresent()) {
                    return winner.get();
                }
            }
            throw e;
        }
        return created;
    }

    private static Optional<KnowledgeObject> load(ForgeClient client, Address.Revision address) {
        if (address == null) {
            return Optional.empty();
        }
        return client.repository().knowledge().view().object(address.node());
    }

    private static CanonicalValue obj(String key, String value) {
        return CanonicalValue.objectBuilder().put(key, value).build();
    }
}

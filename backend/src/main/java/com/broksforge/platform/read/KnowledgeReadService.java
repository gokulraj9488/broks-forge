package com.broksforge.platform.read;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.platform.ForgePlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to Platform V2 Knowledge: reads the projected Provider, Model, Prompt, Dataset,
 * Evaluation and EvaluationVerdict artifacts by their V1 id (resolving the stable kernel {@link Name}s
 * established in P2). Consumes only public Platform V2 APIs; performs no writes.
 *
 * <p>Reads are deterministic (a projection of the current immutable log) and idempotent (side-effect free).
 * Every accessor returns {@link Optional#empty()} when the platform is disabled/unavailable or the artifact
 * has not been projected — the caller then falls back to V1 (see {@link KnowledgeReadFacade}). The whole
 * bean exists only when {@code broksforge.platform.v2.enabled=true}.
 *
 * <p><b>Consumed vs. retained accessors:</b> {@link #providerName} and {@link #promptText} are the read paths
 * actually adopted (P3–P5), because their attributes are projected by the backfill and support a meaningful
 * verified read-through (the value is keyed by a stable id and read back independently). {@link #modelId},
 * {@link #datasetContentHash}, {@link #evaluationMetrics} and {@link #verdictConfidence} are retained for
 * symmetry with the P2 projection API but are intentionally <em>not</em> wired into any read path:
 * {@code model_id} is encoded in its own kernel name (reading it requires already knowing it — no
 * independent parity), and Dataset/Evaluation/Verdict are lawful projections that the backfill does not
 * populate, so no V1 parity can be proven. They are therefore ineligible for read-through by design, not
 * dead by oversight.
 */
@Service
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class KnowledgeReadService {

    private static final ActorId READ_ACTOR = ActorId.of("system:forge-read");

    private final ForgePlatform platform;

    public KnowledgeReadService(ForgePlatform platform) {
        this.platform = platform;
    }

    public Optional<String> providerName(UUID organizationId, UUID providerId) {
        return string(organizationId, Name.of("v1/provider/" + providerId), "name");
    }

    public Optional<String> modelId(UUID organizationId, UUID providerId, String modelId) {
        return string(organizationId, Name.of("v1/model/" + providerId + "/" + modelId), "model_id");
    }

    public Optional<String> promptText(UUID organizationId, UUID promptId) {
        return string(organizationId, Name.of("v1/prompt/" + promptId), "text");
    }

    public Optional<String> datasetContentHash(UUID organizationId, UUID datasetId) {
        return string(organizationId, Name.of("v1/dataset/" + datasetId), "content_hash");
    }

    public Optional<List<String>> evaluationMetrics(UUID organizationId, UUID evaluationId) {
        return artifact(organizationId, Name.of("v1/evaluation/" + evaluationId)).flatMap(obj -> {
            if (obj.payload() instanceof CanonicalValue.Obj o
                    && o.entries().get("metrics") instanceof CanonicalValue.Arr arr) {
                return Optional.of(arr.items().stream()
                        .filter(v -> v instanceof CanonicalValue.Str)
                        .map(v -> ((CanonicalValue.Str) v).value())
                        .toList());
            }
            return Optional.empty();
        });
    }

    public Optional<BigDecimal> verdictConfidence(UUID organizationId, String verdictKey) {
        return artifact(organizationId, Name.of("v1/verdict/" + verdictKey)).flatMap(obj -> {
            if (obj.payload() instanceof CanonicalValue.Obj o
                    && o.entries().get("confidence") instanceof CanonicalValue.Num num) {
                return Optional.of(num.value());
            }
            return Optional.empty();
        });
    }

    // ---- internals ----

    private Optional<String> string(UUID organizationId, Name name, String field) {
        return artifact(organizationId, name).flatMap(obj -> {
            if (obj.payload() instanceof CanonicalValue.Obj o
                    && o.entries().get(field) instanceof CanonicalValue.Str str) {
                return Optional.of(str.value());
            }
            return Optional.empty();
        });
    }

    private Optional<KnowledgeObject> artifact(UUID organizationId, Name name) {
        OrgId org = platform.identity().toOrgId(organizationId);
        ForgeClient client = platform.clientFor(org, READ_ACTOR);
        return client.repository().kernel().resolve(org, name)
                .flatMap(rev -> client.repository().knowledge().view().object(rev.node()));
    }
}

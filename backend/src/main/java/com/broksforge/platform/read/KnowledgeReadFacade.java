package com.broksforge.platform.read;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The single, behavior-identical entry point for Platform V2 Knowledge reads: a <b>verified read-through</b>.
 * It serves the value from Knowledge <em>only when that value is consistent with the current V1 value</em>,
 * and otherwise falls back to V1 — covering the disabled/unavailable, not-yet-projected, and stale-after-edit
 * cases. The served value therefore always equals the current V1 value, while the read is integrated with the
 * Knowledge graph. Reads are deterministic, idempotent, and side-effect free.
 *
 * <p>This is the only design that stays strictly identical to V1 (V1 remains the write system of record, and
 * no dual-write is introduced, so a projected artifact may lag a subsequent V1 edit).
 *
 * <p><b>Centralization (P4):</b> this facade is always a bean, and it — not each caller — owns the
 * platform-disabled/absent fallback. It depends on the {@link KnowledgeReadService} through an
 * {@link ObjectProvider}, which yields the read service only when {@code broksforge.platform.v2.enabled=true}
 * and is empty otherwise. Application services depend on this facade directly and never touch
 * {@link KnowledgeReadService} or repeat the availability check, so read logic is not duplicated across
 * services. When the platform is disabled the facade transparently returns the V1 value.
 */
@Component
public class KnowledgeReadFacade {

    private final ObjectProvider<KnowledgeReadService> knowledge;

    public KnowledgeReadFacade(ObjectProvider<KnowledgeReadService> knowledge) {
        this.knowledge = knowledge;
    }

    /** The provider name, served from Knowledge when consistent with the current V1 value, else V1. */
    public String providerName(UUID organizationId, UUID providerId, String v1Current) {
        return consistentOrFallback(read(k -> k.providerName(organizationId, providerId)), v1Current);
    }

    /** The prompt active-template text, served from Knowledge when consistent with V1, else V1. */
    public String promptText(UUID organizationId, UUID promptId, String v1Current) {
        return consistentOrFallback(read(k -> k.promptText(organizationId, promptId)), v1Current);
    }

    /** Whether Knowledge currently holds a provider name consistent with {@code v1Current} (observability/tests). */
    public boolean isKnowledgeConsistentProviderName(UUID organizationId, UUID providerId, String v1Current) {
        return read(k -> k.providerName(organizationId, providerId)).filter(v1Current::equals).isPresent();
    }

    /** Whether Knowledge currently holds a prompt text consistent with {@code v1Current} (observability/tests). */
    public boolean isKnowledgeConsistentPromptText(UUID organizationId, UUID promptId, String v1Current) {
        return read(k -> k.promptText(organizationId, promptId)).filter(v1Current::equals).isPresent();
    }

    /**
     * Reads through the {@link KnowledgeReadService} when the platform is enabled; returns
     * {@link Optional#empty()} when it is disabled/absent (the single place the availability check lives).
     */
    private <T> Optional<T> read(Function<KnowledgeReadService, Optional<T>> accessor) {
        KnowledgeReadService service = knowledge.getIfAvailable();
        return service == null ? Optional.empty() : accessor.apply(service);
    }

    private static String consistentOrFallback(Optional<String> knowledgeValue, String v1Current) {
        return knowledgeValue.filter(v -> v.equals(v1Current)).orElse(v1Current);
    }
}

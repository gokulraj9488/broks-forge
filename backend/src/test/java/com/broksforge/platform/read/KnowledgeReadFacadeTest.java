package com.broksforge.platform.read;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The verified read-through: serve Knowledge only when consistent with V1, else fall back to V1 — and the
 * facade owns the platform-disabled/absent fallback (empty {@link ObjectProvider}) in one place.
 */
class KnowledgeReadFacadeTest {

    private final UUID org = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    /** An {@link ObjectProvider} that yields the given read service (or none when {@code service} is null). */
    @SuppressWarnings("unchecked")
    private static KnowledgeReadFacade facadeFor(KnowledgeReadService service) {
        ObjectProvider<KnowledgeReadService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return new KnowledgeReadFacade(provider);
    }

    // ---- provider name ----

    @Test
    void servesProviderNameWhenConsistentWithV1() {
        KnowledgeReadService svc = mock(KnowledgeReadService.class);
        when(svc.providerName(any(), any())).thenReturn(Optional.of("Anthropic"));
        assertEquals("Anthropic", facadeFor(svc).providerName(org, id, "Anthropic"));
    }

    @Test
    void providerNameFallsBackToV1WhenKnowledgeIsStale() {
        KnowledgeReadService svc = mock(KnowledgeReadService.class);
        when(svc.providerName(any(), any())).thenReturn(Optional.of("OldName")); // V2 lags a V1 rename
        assertEquals("NewName", facadeFor(svc).providerName(org, id, "NewName"));
    }

    @Test
    void providerNameFallsBackToV1WhenNotProjected() {
        KnowledgeReadService svc = mock(KnowledgeReadService.class);
        when(svc.providerName(any(), any())).thenReturn(Optional.empty());
        assertEquals("V1Name", facadeFor(svc).providerName(org, id, "V1Name"));
    }

    // ---- prompt text (P4) ----

    @Test
    void servesPromptTextWhenConsistentWithV1() {
        KnowledgeReadService svc = mock(KnowledgeReadService.class);
        when(svc.promptText(any(), any())).thenReturn(Optional.of("Answer {{q}}"));
        assertEquals("Answer {{q}}", facadeFor(svc).promptText(org, id, "Answer {{q}}"));
    }

    @Test
    void promptTextFallsBackToV1WhenKnowledgeIsStale() {
        KnowledgeReadService svc = mock(KnowledgeReadService.class);
        when(svc.promptText(any(), any())).thenReturn(Optional.of("Old template"));
        assertEquals("New template", facadeFor(svc).promptText(org, id, "New template"));
    }

    // ---- platform disabled/absent: fallback owned by the facade ----

    @Test
    void fallsBackToV1WhenPlatformDisabled() {
        KnowledgeReadFacade facade = facadeFor(null); // no read service bean → platform disabled/absent
        assertEquals("V1Name", facade.providerName(org, id, "V1Name"));
        assertEquals("V1 template", facade.promptText(org, id, "V1 template"));
    }
}

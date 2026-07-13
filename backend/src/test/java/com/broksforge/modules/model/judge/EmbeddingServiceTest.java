package com.broksforge.modules.model.judge;

import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers provider resolution and endpoint-shape detection without a real network call, mirroring
 * {@link JudgeInvocationServiceTest}. The actual OpenAI/Google embedding HTTP calls are exercised
 * by the manual end-to-end verification.
 */
class EmbeddingServiceTest {

    private ProviderRepository providerRepository;
    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        providerRepository = mock(ProviderRepository.class);
        service = new EmbeddingService(providerRepository, mock(CredentialEncryptionService.class),
                new ProviderAdapterRegistry(List.of()), mock(OutboundUrlGuard.class),
                new ModelInvocationProperties(2000, true, 4000), new ObjectMapper());
    }

    @Test
    @DisplayName("no providerId configured fails clearly")
    void noProviderIdConfigured() {
        EmbeddingResult result = service.embed(null, null, "text");
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("providerId");
    }

    @Test
    @DisplayName("unknown providerId fails clearly")
    void unknownProvider() {
        UUID providerId = UUID.randomUUID();
        when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

        EmbeddingResult result = service.embed(providerId, null, "text");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    @DisplayName("a disabled provider fails clearly")
    void disabledProvider() {
        UUID providerId = UUID.randomUUID();
        Provider provider = new Provider();
        provider.setName("Embedding Provider");
        provider.setEnabled(false);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        EmbeddingResult result = service.embed(providerId, null, "text");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("disabled");
    }

    @Test
    @DisplayName("a provider endpoint shape that isn't OpenAI-compatible or Google fails clearly, never guesses")
    void unsupportedEndpointShape() {
        UUID providerId = UUID.randomUUID();
        Provider provider = new Provider();
        provider.setName("Custom Provider");
        provider.setEnabled(true);
        provider.setBaseUrl("https://internal-llm-gateway.example.com/v1/generate");
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        EmbeddingResult result = service.embed(providerId, null, "text");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("isn't supported");
    }
}

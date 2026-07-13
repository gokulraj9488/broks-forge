package com.broksforge.modules.agent.service;

import com.broksforge.common.security.CredentialEncryptionService;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentCredential;
import com.broksforge.modules.agent.repository.AgentCredentialRepository;
import com.broksforge.modules.agent.web.AgentCredentialMapper;
import com.broksforge.modules.model.adapter.AnthropicAdapter;
import com.broksforge.modules.model.adapter.GoogleAiStudioAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentCredentialService#resolveAuthHeaders} used to know nothing about Providers at all
 * — an agent linked to a Provider but with no credential of its own would silently send zero
 * auth headers, which is exactly the gap the "UI alignment" work surfaced: the frontend now
 * claims "Authentication: Inherited from Provider", so the actual invocation must really inherit
 * it, not just display the claim.
 */
@DisplayName("AgentCredentialService.resolveAuthHeaders — Provider fallback")
class AgentCredentialServiceResolveAuthHeadersTest {

    private AgentCredentialRepository credentialRepository;
    private ProviderRepository providerRepository;
    private CredentialEncryptionService encryptionService;
    private AgentCredentialService service;

    @BeforeEach
    void setUp() {
        credentialRepository = mock(AgentCredentialRepository.class);
        providerRepository = mock(ProviderRepository.class);
        encryptionService = mock(CredentialEncryptionService.class);
        ProviderAdapterRegistry registry = new ProviderAdapterRegistry(
                List.of(new GoogleAiStudioAdapter(), new AnthropicAdapter()));

        service = new AgentCredentialService(credentialRepository, mock(AgentAccessGuard.class),
                mock(AgentCredentialMapper.class), encryptionService, mock(CredentialConnectionTester.class),
                providerRepository, registry);

        when(credentialRepository.findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
    }

    private Agent agentWithProvider(UUID providerId) {
        Agent agent = new Agent();
        agent.setProviderId(providerId);
        return agent;
    }

    private Provider provider(String baseUrl, AgentAuthType authType, String encryptedKey, boolean enabled) {
        Provider provider = new Provider();
        provider.setBaseUrl(baseUrl);
        provider.setAuthType(authType);
        provider.setEncryptedApiKey(encryptedKey);
        provider.setEnabled(enabled);
        return provider;
    }

    @Test
    @DisplayName("no credential, no provider link → no headers (unchanged pre-milestone behaviour)")
    void noCredentialNoProviderYieldsNoHeaders() {
        Agent agent = new Agent();
        assertThat(service.resolveAuthHeaders(agent)).isEmpty();
    }

    @Test
    @DisplayName("an agent with its own credential ignores the linked provider entirely (agent credential wins)")
    void agentOwnCredentialTakesPriorityOverProvider() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        AgentCredential credential = new AgentCredential();
        credential.setAuthType(AgentAuthType.BEARER_TOKEN);
        credential.setEncryptedSecret("cipher");
        when(credentialRepository.findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(agent.getId()))
                .thenReturn(Optional.of(credential));
        when(encryptionService.decrypt("cipher")).thenReturn("agent-own-secret");

        Map<String, String> headers = service.resolveAuthHeaders(agent);

        assertThat(headers).containsEntry("Authorization", "Bearer agent-own-secret");
        org.mockito.Mockito.verifyNoInteractions(providerRepository);
    }

    @Test
    @DisplayName("no agent credential + Google AI Studio provider → x-goog-api-key header")
    void fallsBackToGoogleProviderAuth() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        Provider provider = provider(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                AgentAuthType.API_KEY, "enc-google-key", true);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("enc-google-key")).thenReturn("google-real-key");

        Map<String, String> headers = service.resolveAuthHeaders(agent);

        assertThat(headers).containsExactly(Map.entry("x-goog-api-key", "google-real-key"));
    }

    @Test
    @DisplayName("no agent credential + Anthropic provider → x-api-key plus the required anthropic-version header")
    void fallsBackToAnthropicProviderAuth() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        Provider provider = provider("https://api.anthropic.com/v1/messages", AgentAuthType.API_KEY,
                "enc-anthropic-key", true);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("enc-anthropic-key")).thenReturn("anthropic-real-key");

        Map<String, String> headers = service.resolveAuthHeaders(agent);

        assertThat(headers).containsEntry("x-api-key", "anthropic-real-key")
                .containsEntry("anthropic-version", "2023-06-01");
    }

    @Test
    @DisplayName("a provider with no adapter match falls back to a generic Authorization: Bearer header")
    void fallsBackToGenericBearerForUnmatchedProvider() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        Provider provider = provider("https://internal-llm-gateway.example.com/v1/generate",
                AgentAuthType.BEARER_TOKEN, "enc-generic-key", true);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(encryptionService.decrypt("enc-generic-key")).thenReturn("generic-real-key");

        Map<String, String> headers = service.resolveAuthHeaders(agent);

        assertThat(headers).containsExactly(Map.entry("Authorization", "Bearer generic-real-key"));
    }

    @Test
    @DisplayName("a disabled provider yields no auth headers (consistent with it being blocked from invocation)")
    void disabledProviderYieldsNoHeaders() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        Provider provider = provider("https://api.anthropic.com/v1/messages", AgentAuthType.API_KEY,
                "enc-key", false);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        assertThat(service.resolveAuthHeaders(agent)).isEmpty();
    }

    @Test
    @DisplayName("a provider with authType NONE or no stored key yields no headers, not a decrypt error")
    void providerWithNoAuthConfiguredYieldsNoHeaders() {
        UUID providerId = UUID.randomUUID();
        Agent agent = agentWithProvider(providerId);
        Provider provider = provider("https://api.anthropic.com/v1/messages", AgentAuthType.NONE, null, true);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        assertThat(service.resolveAuthHeaders(agent)).isEmpty();
    }
}

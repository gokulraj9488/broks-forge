package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.domain.EvaluationProfileVersion;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.evaluation.repository.EvaluationProfileVersionRepository;
import com.broksforge.modules.evaluation.web.EvaluationMapper;
import com.broksforge.modules.evaluation.web.dto.MetricSpecDto;
import com.broksforge.modules.evaluation.web.dto.UpdateEvaluationProfileRequest;
import com.broksforge.modules.model.judge.ChatModelDiscoveryService;
import com.broksforge.modules.model.judge.EmbeddingModelDiscoveryService;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * On save (strict validation), a judge-family/embedding metric's provider must actually exist,
 * be reachable, and support the metric's required capability — a UUID-shaped providerId that
 * belongs to no real provider, or a provider that can't do embeddings/chat, must be rejected
 * before the profile is saved rather than surfacing as a "Model Not Found" execution failure
 * only once a job actually runs against it.
 */
@DisplayName("EvaluationProfileService — provider/model/capability validation on save")
class EvaluationProfileServiceValidationTest {

    private final EvaluationProfileRepository profileRepository = mock(EvaluationProfileRepository.class);
    private final EvaluationProfileVersionRepository versionRepository = mock(EvaluationProfileVersionRepository.class);
    private final EvaluationAccessGuard accessGuard = mock(EvaluationAccessGuard.class);
    private final OrganizationAccessService accessService = mock(OrganizationAccessService.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final EvaluationMapper mapper = mock(EvaluationMapper.class);
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final EmbeddingModelDiscoveryService embeddingModelDiscoveryService = mock(EmbeddingModelDiscoveryService.class);
    private final ChatModelDiscoveryService chatModelDiscoveryService = mock(ChatModelDiscoveryService.class);

    private final EvaluationProfileService service = new EvaluationProfileService(profileRepository, versionRepository,
            accessGuard, accessService, projectService, mapper, providerRepository, embeddingModelDiscoveryService,
            chatModelDiscoveryService);

    private final UUID actorId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    private EvaluationProfile profile;

    @BeforeEach
    void setUp() {
        profile = new EvaluationProfile();
        profile.setId(profileId);
        profile.setOrganizationId(organizationId);
        profile.setProjectId(projectId);
        profile.setMetrics(List.of());
        when(accessGuard.requireManageableProfile(organizationId, projectId, profileId, actorId, OrganizationRole.MEMBER))
                .thenReturn(profile);
        when(mapper.toProfileResponse(any())).thenReturn(null);
    }

    private UpdateEvaluationProfileRequest requestWith(EvaluationMetricType type, Map<String, Object> params) {
        return new UpdateEvaluationProfileRequest(null, null,
                List.of(new MetricSpecDto(type, null, null, null, params)), null);
    }

    @Test
    @DisplayName("rejects a providerId that doesn't belong to any real provider in this project")
    void rejectsUnknownProvider() {
        when(providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(actorId, organizationId, projectId, profileId,
                requestWith(EvaluationMetricType.SEMANTIC_SIMILARITY, Map.of("providerId", providerId.toString()))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    @DisplayName("rejects a provider that doesn't support embeddings for SEMANTIC_SIMILARITY")
    void rejectsUnsupportedEmbeddingCapability() {
        Provider provider = providerWith("Groq");
        when(providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId))
                .thenReturn(Optional.of(provider));
        when(embeddingModelDiscoveryService.listEmbeddingModels(provider))
                .thenReturn(new EmbeddingModelDiscoveryService.Result(false, List.of(),
                        "doesn't offer an embeddings API"));

        assertThatThrownBy(() -> service.update(actorId, organizationId, projectId, profileId,
                requestWith(EvaluationMetricType.SEMANTIC_SIMILARITY, Map.of("providerId", providerId.toString()))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("doesn't offer an embeddings API");
    }

    @Test
    @DisplayName("rejects an unreachable provider (discovery call failed) rather than saving silently")
    void rejectsUnreachableProvider() {
        Provider provider = providerWith("OpenAI");
        when(providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId))
                .thenReturn(Optional.of(provider));
        when(chatModelDiscoveryService.listChatModels(provider))
                .thenReturn(new ChatModelDiscoveryService.Result(true, List.of(), "Couldn't list models: timeout"));
        // (constructed directly — Result's static factories are package-private to model.judge)

        assertThatThrownBy(() -> service.update(actorId, organizationId, projectId, profileId,
                requestWith(EvaluationMetricType.LLM_JUDGE, Map.of("providerId", providerId.toString()))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("rejects a configured model that isn't in the provider's discovered model list")
    void rejectsUnknownModel() {
        Provider provider = providerWith("OpenAI");
        when(providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId))
                .thenReturn(Optional.of(provider));
        when(chatModelDiscoveryService.listChatModels(provider))
                .thenReturn(new ChatModelDiscoveryService.Result(true, List.of("gpt-4o", "gpt-4o-mini"), null));

        assertThatThrownBy(() -> service.update(actorId, organizationId, projectId, profileId,
                requestWith(EvaluationMetricType.LLM_JUDGE,
                        Map.of("providerId", providerId.toString(), "model", "not-a-real-model"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("was not found for provider");
    }

    @Test
    @DisplayName("accepts a reachable provider whose discovered models include the configured one")
    void acceptsValidProviderAndModel() {
        Provider provider = providerWith("OpenAI");
        when(providerRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId))
                .thenReturn(Optional.of(provider));
        when(chatModelDiscoveryService.listChatModels(provider))
                .thenReturn(new ChatModelDiscoveryService.Result(true, List.of("gpt-4o", "gpt-4o-mini"), null));
        when(versionRepository.save(any())).thenAnswer(inv -> {
            EvaluationProfileVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        assertThat(service.update(actorId, organizationId, projectId, profileId,
                requestWith(EvaluationMetricType.LLM_JUDGE,
                        Map.of("providerId", providerId.toString(), "model", "gpt-4o")))).isNull(); // mapper stubbed to null
    }

    private Provider providerWith(String name) {
        Provider provider = new Provider();
        provider.setId(providerId);
        provider.setName(name);
        return provider;
    }
}

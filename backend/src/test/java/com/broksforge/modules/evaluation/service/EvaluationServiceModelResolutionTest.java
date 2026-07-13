package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.config.properties.EvaluationProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.agent.service.AgentInvocationService;
import com.broksforge.modules.agent.service.AgentInvocationTarget;
import com.broksforge.modules.dataset.service.DatasetService;
import com.broksforge.modules.dataset.service.DatasetVersionRef;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.evaluation.repository.EvaluationResultRepository;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import com.broksforge.modules.evaluation.web.EvaluationMapper;
import com.broksforge.modules.evaluation.web.dto.CreateEvaluationJobRequest;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.prompt.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the model resolution precedence at evaluation-job creation time
 * (job override &gt; agent version model &gt; provider default model), and the fail-fast
 * validation that rejects a job before any HTTP call when a hosted-provider endpoint
 * (OpenAI-compatible, Anthropic, or native Ollama) has no model resolvable at all —
 * the gap that let native Ollama jobs reach the provider and fail with
 * "HTTP 400 model is required" instead of failing validation up front.
 */
@DisplayName("EvaluationService (model resolution precedence)")
class EvaluationServiceModelResolutionTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final UUID DATASET_ID = UUID.randomUUID();

    private AgentInvocationService agentInvocationService;
    private DatasetService datasetService;
    private EvaluationJobLifecycle lifecycle;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        agentInvocationService = mock(AgentInvocationService.class);
        datasetService = mock(DatasetService.class);
        lifecycle = mock(EvaluationJobLifecycle.class);
        when(lifecycle.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(datasetService.resolveVersionForExecution(any(), any(), any(), any(), any()))
                .thenReturn(new DatasetVersionRef(UUID.randomUUID(), 1, 5));

        service = new EvaluationService(
                mock(EvaluationJobRepository.class),
                mock(EvaluationRunRepository.class),
                mock(EvaluationResultRepository.class),
                mock(EvaluationProfileRepository.class),
                mock(EvaluationProfileService.class),
                mock(EvaluationAccessGuard.class),
                mock(OrganizationAccessService.class),
                mock(ProjectService.class),
                agentInvocationService,
                datasetService,
                mock(PromptService.class),
                mock(EvaluationJobExecutor.class),
                lifecycle,
                mock(EvaluationPlanBuilder.class),
                mock(EvaluationBackgroundRunner.class),
                mock(EvaluationJobEventService.class),
                mock(EvaluationMapper.class),
                new EvaluationProperties(500, 50, 4, 4, 3, 1000, 30000, 300000));
    }

    private AgentInvocationTarget target(String endpointUrl, String versionModel, String providerDefaultModel) {
        return new AgentInvocationTarget(AGENT_ID, UUID.randomUUID(), endpointUrl, Map.of(),
                versionModel, providerDefaultModel);
    }

    private CreateEvaluationJobRequest request(String model) {
        return new CreateEvaluationJobRequest("Job", AGENT_ID, null, DATASET_ID, null, null, null, null,
                null, model, null, null);
    }

    @Test
    @DisplayName("falls back to the provider's default model when neither the job nor the agent version set one")
    void fallsBackToProviderDefaultModel() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("http://host.docker.internal:11434/api/chat", null, "llama3.2:1b"));

        ArgumentCaptor<EvaluationJob> captor = ArgumentCaptor.forClass(EvaluationJob.class);
        service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request(null));

        org.mockito.Mockito.verify(lifecycle).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("llama3.2:1b");
    }

    @Test
    @DisplayName("the agent version's model wins over the provider default")
    void agentVersionOverridesProviderDefault() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("http://host.docker.internal:11434/api/chat", "qwen2.5-coder:7b", "llama3.2:1b"));

        ArgumentCaptor<EvaluationJob> captor = ArgumentCaptor.forClass(EvaluationJob.class);
        service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request(null));

        org.mockito.Mockito.verify(lifecycle).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("qwen2.5-coder:7b");
    }

    @Test
    @DisplayName("the evaluation job's own model override wins over both the agent version and provider default")
    void evaluationOverrideWinsOverEverything() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("http://host.docker.internal:11434/api/chat", "qwen2.5-coder:7b", "llama3.2:1b"));

        ArgumentCaptor<EvaluationJob> captor = ArgumentCaptor.forClass(EvaluationJob.class);
        service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request("nomic-embed-text"));

        org.mockito.Mockito.verify(lifecycle).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("nomic-embed-text");
    }

    @Test
    @DisplayName("rejects job creation when no model resolves at all for a native Ollama endpoint")
    void rejectsWhenNoModelResolvesForOllama() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("http://host.docker.internal:11434/api/chat", null, null));

        assertThatThrownBy(() -> service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("requires a model");
    }

    @Test
    @DisplayName("a plain CUSTOM_REST endpoint never requires a model")
    void customRestNeverRequiresModel() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("https://my-agent.example.com/invoke", null, null));

        ArgumentCaptor<EvaluationJob> captor = ArgumentCaptor.forClass(EvaluationJob.class);
        service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request(null));

        org.mockito.Mockito.verify(lifecycle).save(captor.capture());
        assertThat(captor.getValue().getModel()).isNull();
    }

    @Test
    @DisplayName("rejects job creation when no model resolves for an Anthropic /messages endpoint")
    void rejectsWhenNoModelResolvesForAnthropic() {
        when(agentInvocationService.resolveTarget(ACTOR_ID, ORG_ID, PROJECT_ID, AGENT_ID))
                .thenReturn(target("https://api.anthropic.com/v1/messages", null, null));

        assertThatThrownBy(() -> service.create(ACTOR_ID, ORG_ID, PROJECT_ID, request(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("requires a model");
    }
}

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers every failure branch reachable without a real network call — provider resolution,
 * disabled-provider, missing-model, and judge-response parsing (including markdown-fenced JSON,
 * which judge models routinely emit despite being told not to). The actual HTTP call is exercised
 * only by the manual end-to-end verification (a live Google AI Studio provider), consistent with
 * every other outbound-call class in this codebase (e.g. {@code AgentEndpointInvoker}) having no
 * mocked-HTTP unit test either.
 */
class JudgeInvocationServiceTest {

    private ProviderRepository providerRepository;
    private JudgeInvocationService service;

    @BeforeEach
    void setUp() {
        providerRepository = mock(ProviderRepository.class);
        service = new JudgeInvocationService(providerRepository, mock(CredentialEncryptionService.class),
                new ProviderAdapterRegistry(List.of()), mock(OutboundUrlGuard.class),
                new ModelInvocationProperties(2000, true, 4000), new ObjectMapper());
    }

    @Test
    @DisplayName("no providerId configured fails clearly")
    void noProviderIdConfigured() {
        JudgeVerdict verdict = service.judge(null, "gpt-4o", "prompt");
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("providerId");
    }

    @Test
    @DisplayName("unknown providerId fails clearly")
    void unknownProvider() {
        UUID providerId = UUID.randomUUID();
        when(providerRepository.findById(providerId)).thenReturn(Optional.empty());

        JudgeVerdict verdict = service.judge(providerId, "gpt-4o", "prompt");

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("not found");
    }

    @Test
    @DisplayName("a disabled provider fails clearly, consistent with it being blocked from invocation elsewhere")
    void disabledProvider() {
        UUID providerId = UUID.randomUUID();
        Provider provider = new Provider();
        provider.setName("Judge Provider");
        provider.setEnabled(false);
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        JudgeVerdict verdict = service.judge(providerId, "gpt-4o", "prompt");

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("disabled");
    }

    @Test
    @DisplayName("no model param and no provider default model fails clearly")
    void noModelConfigured() {
        UUID providerId = UUID.randomUUID();
        Provider provider = new Provider();
        provider.setName("Judge Provider");
        provider.setEnabled(true);
        provider.setBaseUrl("https://api.openai.com/v1/chat/completions");
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));

        JudgeVerdict verdict = service.judge(providerId, null, "prompt");

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("No judge model configured");
    }

    @Test
    @DisplayName("parses strict JSON judge output")
    void parsesStrictJson() {
        JudgeVerdict verdict = service.parseVerdict("{\"score\": 0.85, \"reasoning\": \"Solid answer\"}");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.score()).isEqualByComparingTo("0.85");
        assertThat(verdict.reasoning()).isEqualTo("Solid answer");
    }

    @Test
    @DisplayName("parses an optional per-criterion breakdown alongside the overall score")
    void parsesCriteriaBreakdown() {
        JudgeVerdict verdict = service.parseVerdict(
                "{\"score\": 0.93, \"reasoning\": \"Great\", \"criteria\": {\"Correctness\": 9, \"Helpfulness\": 10}}");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.criteria()).containsEntry("Correctness", 9).containsEntry("Helpfulness", 10);
    }

    @Test
    @DisplayName("criteria is an empty map, not null, when the judge omits it")
    void criteriaDefaultsToEmptyMap() {
        JudgeVerdict verdict = service.parseVerdict("{\"score\": 0.5}");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.criteria()).isEmpty();
    }

    @Test
    @DisplayName("tolerates a markdown code fence wrapped around the JSON")
    void tolatesMarkdownFence() {
        JudgeVerdict verdict = service.parseVerdict("```json\n{\"score\": 1.0, \"reasoning\": \"Perfect\"}\n```");
        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("fails clearly when the judge's response has no JSON object at all")
    void failsWithNoJson() {
        JudgeVerdict verdict = service.parseVerdict("The answer is great, I'd give it a 9 out of 10.");
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("did not return parseable JSON");
    }

    @Test
    @DisplayName("fails clearly when the JSON is missing the required score field")
    void failsWithMissingScoreField() {
        JudgeVerdict verdict = service.parseVerdict("{\"reasoning\": \"Good\"}");
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("missing a numeric \"score\"");
    }

    @Test
    @DisplayName("fails clearly on a blank response")
    void failsOnBlankResponse() {
        JudgeVerdict verdict = service.parseVerdict("   ");
        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.error()).contains("empty response");
    }
}

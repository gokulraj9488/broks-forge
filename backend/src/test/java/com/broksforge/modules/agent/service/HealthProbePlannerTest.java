package com.broksforge.modules.agent.service;

import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.HealthProbeStrategy;
import com.broksforge.modules.agent.domain.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthProbePlanner (provider-aware probing)")
class HealthProbePlannerTest {

    @Test
    @DisplayName("OpenAI-compatible chat endpoints validate via GET .../models (token-free)")
    void openAiCompatibleUsesModelsEndpoint() {
        assertModels("https://api.groq.com/openai/v1/chat/completions",
                "https://api.groq.com/openai/v1/models");
        assertModels("https://api.openai.com/v1/chat/completions",
                "https://api.openai.com/v1/models");
        assertModels("https://openrouter.ai/api/v1/chat/completions",
                "https://openrouter.ai/api/v1/models");
        assertModels("http://localhost:11434/v1/chat/completions",
                "http://localhost:11434/v1/models");
    }

    @Test
    @DisplayName("Anthropic /messages and Gemini generateContent derive their models list")
    void anthropicAndGemini() {
        assertModels("https://api.anthropic.com/v1/messages",
                "https://api.anthropic.com/v1/models");
        assertModels("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models");
    }

    @Test
    @DisplayName("self-hosted frameworks keep their dedicated health endpoints")
    void frameworkHealthEndpoints() {
        HealthProbePlanner.ProbePlan spring =
                HealthProbePlanner.plan("https://svc.example.com/api", AgentFramework.SPRING_AI, null);
        assertThat(spring.strategy()).isEqualTo(HealthProbeStrategy.GET_ACTUATOR_HEALTH);
        assertThat(spring.url()).isEqualTo("https://svc.example.com/actuator/health");

        HealthProbePlanner.ProbePlan lang =
                HealthProbePlanner.plan("https://svc.example.com/invoke", AgentFramework.LANGGRAPH, null);
        assertThat(lang.strategy()).isEqualTo(HealthProbeStrategy.GET_HEALTH);
        assertThat(lang.url()).isEqualTo("https://svc.example.com/health");
    }

    @Test
    @DisplayName("an endpoint that already targets a health path is honoured verbatim")
    void honoursExplicitHealthPath() {
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan("https://svc.example.com/health", AgentFramework.CUSTOM_REST, null);
        assertThat(plan.strategy()).isEqualTo(HealthProbeStrategy.GET_HEALTH);
        assertThat(plan.url()).isEqualTo("https://svc.example.com/health");
    }

    @Test
    @DisplayName("a generic REST endpoint falls back to a plain GET")
    void genericFallsBackToGetRoot() {
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan("https://svc.example.com/run", AgentFramework.CUSTOM_REST, null);
        assertThat(plan.strategy()).isEqualTo(HealthProbeStrategy.GET_ROOT);
        assertThat(plan.url()).isEqualTo("https://svc.example.com/run");
    }

    @Test
    @DisplayName("detects the provider from the endpoint host")
    void detectsProviderFromHost() {
        assertThat(HealthProbePlanner.detectProvider("api.groq.com")).isEqualTo(LlmProvider.GROQ);
        assertThat(HealthProbePlanner.detectProvider("api.openai.com")).isEqualTo(LlmProvider.OPENAI);
        assertThat(HealthProbePlanner.detectProvider("api.anthropic.com")).isEqualTo(LlmProvider.ANTHROPIC);
        assertThat(HealthProbePlanner.detectProvider("example.com")).isNull();
    }

    private void assertModels(String endpoint, String expectedUrl) {
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan(endpoint, AgentFramework.CUSTOM_REST, null);
        assertThat(plan.strategy()).isEqualTo(HealthProbeStrategy.GET_MODELS);
        assertThat(plan.method()).isEqualTo(HttpMethod.GET);
        assertThat(plan.url()).isEqualTo(expectedUrl);
    }
}

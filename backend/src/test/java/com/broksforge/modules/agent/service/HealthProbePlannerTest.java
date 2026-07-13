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
    @DisplayName("Ollama's native /api/chat validates via GET .../api/tags, never /v1/models")
    void ollamaNativeUsesApiTags() {
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan("http://localhost:11434/api/chat", AgentFramework.CUSTOM_REST, LlmProvider.OLLAMA);
        assertThat(plan.strategy()).isEqualTo(HealthProbeStrategy.GET_MODELS);
        assertThat(plan.method()).isEqualTo(HttpMethod.GET);
        assertThat(plan.url()).isEqualTo("http://localhost:11434/api/tags");

        // Provider detected purely from a localhost host (no stored provider on the agent version)
        // still resolves to /api/tags, not the OpenAI-compatible fallback.
        HealthProbePlanner.ProbePlan detected =
                HealthProbePlanner.plan("http://localhost:11434/api/chat", AgentFramework.CUSTOM_REST, null);
        assertThat(detected.url()).isEqualTo("http://localhost:11434/api/tags");
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

    @Test
    @DisplayName("requiresModelField covers every adapter shape whose body requires \"model\"")
    void requiresModelFieldCoversAllModelBodyShapes() {
        assertThat(HealthProbePlanner.requiresModelField("https://api.groq.com/openai/v1/chat/completions")).isTrue();
        assertThat(HealthProbePlanner.requiresModelField("https://api.anthropic.com/v1/messages")).isTrue();
        assertThat(HealthProbePlanner.requiresModelField("http://host.docker.internal:11434/api/chat")).isTrue();
        assertThat(HealthProbePlanner.requiresModelField("http://localhost:11434/api/chat")).isTrue();
    }

    @Test
    @DisplayName("requiresModelField is false for Google AI Studio (model is in the URL path) and generic REST")
    void requiresModelFieldExcludesUrlEmbeddedAndGenericShapes() {
        assertThat(HealthProbePlanner.requiresModelField(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")).isFalse();
        assertThat(HealthProbePlanner.requiresModelField("https://my-agent.example.com/invoke")).isFalse();
    }

    private void assertModels(String endpoint, String expectedUrl) {
        HealthProbePlanner.ProbePlan plan =
                HealthProbePlanner.plan(endpoint, AgentFramework.CUSTOM_REST, null);
        assertThat(plan.strategy()).isEqualTo(HealthProbeStrategy.GET_MODELS);
        assertThat(plan.method()).isEqualTo(HttpMethod.GET);
        assertThat(plan.url()).isEqualTo(expectedUrl);
    }
}

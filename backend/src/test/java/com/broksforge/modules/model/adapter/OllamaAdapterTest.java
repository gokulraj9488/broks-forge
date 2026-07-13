package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OllamaAdapter")
class OllamaAdapterTest {

    private final OllamaAdapter adapter = new OllamaAdapter();

    @Test
    @DisplayName("recognises the native /api/chat route, not the OpenAI-compatible route")
    void supportsNativeChatRoute() {
        assertThat(adapter.supportsEndpoint("http://localhost:11434/api/chat")).isTrue();
        assertThat(adapter.supportsEndpoint("http://localhost:11434/v1/chat/completions")).isFalse();
        assertThat(adapter.supportsEndpoint("http://localhost:11434/api/generate")).isFalse();
    }

    @Test
    @DisplayName("providerType is OLLAMA")
    void providerType() {
        assertThat(adapter.providerType()).isEqualTo(LlmProvider.OLLAMA);
    }

    @Test
    @DisplayName("builds a non-streaming messages request")
    void buildsPayload() {
        Map<String, Object> payload = adapter.buildPayload("llama3", "hello", Map.of(), 1024);
        assertThat(payload.get("model")).isEqualTo("llama3");
        assertThat(payload.get("stream")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        var messages = (java.util.List<Map<String, Object>>) payload.get("messages");
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "hello");
    }

    @Test
    @DisplayName("the payload always contains both \"model\" and \"messages\" when a model is given")
    void payloadAlwaysContainsModelAndMessages() {
        Map<String, Object> payload = adapter.buildPayload("qwen2.5-coder:7b", "hello", Map.of(), 1024);
        assertThat(payload).containsKey("model");
        assertThat(payload).containsKey("messages");
        assertThat(payload.get("model")).isEqualTo("qwen2.5-coder:7b");
    }

    @Test
    @DisplayName("parses the single-message response shape and prompt/eval token counts")
    void parsesNativeResponse() {
        String body = """
                {"message":{"role":"assistant","content":"Hi there."},
                 "prompt_eval_count":10,"eval_count":5,"done":true}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEqualTo("Hi there.");
        assertThat(parsed.promptTokens()).isEqualTo(10);
        assertThat(parsed.completionTokens()).isEqualTo(5);
        assertThat(parsed.totalTokens()).isEqualTo(15);
    }

    @Test
    @DisplayName("extracts the bare string error Ollama returns")
    void extractsBareStringError() {
        assertThat(adapter.parseError("{\"error\":\"model 'llama99' not found\"}"))
                .isEqualTo("model 'llama99' not found");
    }
}

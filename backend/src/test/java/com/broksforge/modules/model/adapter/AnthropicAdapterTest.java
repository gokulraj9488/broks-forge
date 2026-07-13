package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnthropicAdapter")
class AnthropicAdapterTest {

    private final AnthropicAdapter adapter = new AnthropicAdapter();

    @Test
    @DisplayName("recognises the Messages API route, not other paths")
    void supportsMessagesRoute() {
        assertThat(adapter.supportsEndpoint("https://api.anthropic.com/v1/messages")).isTrue();
        assertThat(adapter.supportsEndpoint("https://api.anthropic.com/v1/models")).isFalse();
        assertThat(adapter.supportsEndpoint(null)).isFalse();
    }

    @Test
    @DisplayName("providerType is ANTHROPIC")
    void providerType() {
        assertThat(adapter.providerType()).isEqualTo(LlmProvider.ANTHROPIC);
    }

    @Test
    @DisplayName("builds a messages request with a required max_tokens default when the caller specified none")
    void buildsPayloadWithRequiredMaxTokens() {
        Map<String, Object> payload = adapter.buildPayload("claude-sonnet-4", "What is the refund policy?",
                Map.of(), 512);

        assertThat(payload.get("model")).isEqualTo("claude-sonnet-4");
        assertThat(payload.get("max_tokens")).isEqualTo(512);
        @SuppressWarnings("unchecked")
        var messages = (java.util.List<Map<String, Object>>) payload.get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content",
                "What is the refund policy?");
    }

    @Test
    @DisplayName("never overrides an explicit caller-supplied max_tokens")
    void doesNotOverrideExplicitMaxTokens() {
        Map<String, Object> payload = adapter.buildPayload("claude-sonnet-4", "hi", Map.of("max_tokens", 2048), 512);
        assertThat(payload.get("max_tokens")).isEqualTo(2048);
    }

    @Test
    @DisplayName("parses the content-block array response shape, not OpenAI's choices[] shape")
    void parsesContentBlockResponse() {
        String body = """
                {"content":[{"type":"text","text":"We offer a 30-day refund."}],
                 "usage":{"input_tokens":12,"output_tokens":8}}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEqualTo("We offer a 30-day refund.");
        assertThat(parsed.promptTokens()).isEqualTo(12);
        assertThat(parsed.completionTokens()).isEqualTo(8);
        assertThat(parsed.totalTokens()).isEqualTo(20);
    }

    @Test
    @DisplayName("joins multiple text content blocks")
    void joinsMultipleContentBlocks() {
        String body = """
                {"content":[{"type":"text","text":"Part one."},{"type":"text","text":"Part two."}]}""";
        assertThat(adapter.parseSuccess(body).output()).isEqualTo("Part one.\nPart two.");
    }

    @Test
    @DisplayName("extracts the real error message from Anthropic's error envelope")
    void extractsErrorMessage() {
        String body = """
                {"type":"error","error":{"type":"invalid_request_error","message":"max_tokens is required"}}""";
        assertThat(adapter.parseError(body)).contains("max_tokens is required").contains("type=invalid_request_error");
    }
}

package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoogleAiStudioAdapter")
class GoogleAiStudioAdapterTest {

    private final GoogleAiStudioAdapter adapter = new GoogleAiStudioAdapter();

    @Test
    @DisplayName("recognises the generateContent and streamGenerateContent routes, not other Gemini routes")
    void supportsGenerateContentRoutes() {
        assertThat(adapter.supportsEndpoint(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")).isTrue();
        assertThat(adapter.supportsEndpoint(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:streamGenerateContent"))
                .isTrue();
        assertThat(adapter.supportsEndpoint(
                "https://generativelanguage.googleapis.com/v1beta/models")).isFalse();
        assertThat(adapter.supportsEndpoint(null)).isFalse();
    }

    @Test
    @DisplayName("providerType is GOOGLE_GEMINI")
    void providerType() {
        assertThat(adapter.providerType()).isEqualTo(LlmProvider.GOOGLE_GEMINI);
    }

    @Test
    @DisplayName("substitutes the requested model into the URL path, supporting any current or future Gemini model")
    void resolvesModelIntoUrl() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

        assertThat(adapter.resolveInvocationUrl(url, "gemini-2.5-pro"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent");
        assertThat(adapter.resolveInvocationUrl(url, "gemini-3.0-ultra-preview"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.0-ultra-preview:generateContent");
    }

    @Test
    @DisplayName("leaves the URL unchanged when no model override is given, or the URL has no model segment")
    void leavesUrlUnchangedWithoutOverride() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
        assertThat(adapter.resolveInvocationUrl(url, null)).isEqualTo(url);
        assertThat(adapter.resolveInvocationUrl(url, "")).isEqualTo(url);
        assertThat(adapter.resolveInvocationUrl("https://example.com/other", "gemini-2.5-pro"))
                .isEqualTo("https://example.com/other");
    }

    @Test
    @DisplayName("preserves the streaming route suffix when substituting the model")
    void preservesStreamingRouteSuffix() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.5-flash:streamGenerateContent";
        assertThat(adapter.resolveInvocationUrl(url, "gemini-2.5-pro"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-2.5-pro:streamGenerateContent");
    }

    @Test
    @DisplayName("builds a contents/generationConfig request, mapping OpenAI-style parameter names to Gemini's own")
    void buildsPayloadWithMappedParameters() {
        Map<String, Object> payload = adapter.buildPayload("gemini-2.5-flash", "What is the refund policy?",
                Map.of("max_tokens", 512, "top_p", 0.9), 1024);

        @SuppressWarnings("unchecked")
        var contents = (java.util.List<Map<String, Object>>) payload.get("contents");
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0)).containsEntry("role", "user");
        @SuppressWarnings("unchecked")
        var parts = (java.util.List<Map<String, Object>>) contents.get(0).get("parts");
        assertThat(parts.get(0)).containsEntry("text", "What is the refund policy?");

        @SuppressWarnings("unchecked")
        var generationConfig = (Map<String, Object>) payload.get("generationConfig");
        assertThat(generationConfig).containsEntry("maxOutputTokens", 512).containsEntry("topP", 0.9);
        assertThat(generationConfig).doesNotContainKey("max_tokens").doesNotContainKey("top_p");
    }

    @Test
    @DisplayName("applies the default maxOutputTokens only when the caller specified neither style of the parameter")
    void appliesDefaultMaxOutputTokens() {
        Map<String, Object> payload = adapter.buildPayload("gemini-2.5-flash", "hi", Map.of(), 777);
        @SuppressWarnings("unchecked")
        var generationConfig = (Map<String, Object>) payload.get("generationConfig");
        assertThat(generationConfig).containsEntry("maxOutputTokens", 777);
    }

    @Test
    @DisplayName("never overrides an explicit maxOutputTokens the caller already set")
    void doesNotOverrideExplicitMaxOutputTokens() {
        Map<String, Object> payload = adapter.buildPayload("gemini-2.5-flash", "hi",
                Map.of("maxOutputTokens", 2048), 777);
        @SuppressWarnings("unchecked")
        var generationConfig = (Map<String, Object>) payload.get("generationConfig");
        assertThat(generationConfig).containsEntry("maxOutputTokens", 2048);
    }

    @Test
    @DisplayName("parses a normal response: joined text parts and usageMetadata token counts")
    void parsesNormalResponse() {
        String body = """
                {"candidates":[{"content":{"parts":[{"text":"We offer a 30-day refund."}],"role":"model"},
                 "finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":8,"totalTokenCount":20}}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEqualTo("We offer a 30-day refund.");
        assertThat(parsed.promptTokens()).isEqualTo(12);
        assertThat(parsed.completionTokens()).isEqualTo(8);
        assertThat(parsed.totalTokens()).isEqualTo(20);
        assertThat(parsed.blockedReason()).isNull();
    }

    @Test
    @DisplayName("joins multiple text parts from a single candidate")
    void joinsMultipleTextParts() {
        String body = """
                {"candidates":[{"content":{"parts":[{"text":"Part one."},{"text":"Part two."}]},
                 "finishReason":"STOP"}]}""";
        assertThat(adapter.parseSuccess(body).output()).isEqualTo("Part one.\nPart two.");
    }

    @Test
    @DisplayName("detects a safety-blocked candidate (HTTP 200, empty content, finishReason=SAFETY) as blocked")
    void detectsSafetyBlockedCandidate() {
        String body = """
                {"candidates":[{"finishReason":"SAFETY",
                 "safetyRatings":[{"category":"HARM_CATEGORY_DANGEROUS_CONTENT","probability":"HIGH"}]}]}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEmpty();
        assertThat(parsed.blockedReason()).contains("SAFETY").contains("HARM_CATEGORY_DANGEROUS_CONTENT");
    }

    @Test
    @DisplayName("detects a prompt-level block (no candidates array at all, promptFeedback.blockReason present)")
    void detectsPromptLevelBlock() {
        String body = """
                {"promptFeedback":{"blockReason":"OTHER"}}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEmpty();
        assertThat(parsed.blockedReason()).contains("OTHER");
    }

    @Test
    @DisplayName("MAX_TOKENS finish reason with real partial content is a normal success, not a block")
    void maxTokensFinishReasonIsNotABlock() {
        String body = """
                {"candidates":[{"content":{"parts":[{"text":"Partial answer that got cut off"}]},
                 "finishReason":"MAX_TOKENS"}]}""";

        ParsedInvocation parsed = adapter.parseSuccess(body);

        assertThat(parsed.output()).isEqualTo("Partial answer that got cut off");
        assertThat(parsed.blockedReason()).isNull();
    }

    @Test
    @DisplayName("extracts the real error message and status from Gemini's error envelope")
    void extractsErrorMessage() {
        String body = """
                {"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""";
        assertThat(adapter.parseError(body)).contains("API key not valid").contains("status=INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("builds the streaming URL variant from the non-streaming URL")
    void buildsStreamingUrl() {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
        assertThat(adapter.streamingInvocationUrl(url)).isEqualTo(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent");
    }

    @Test
    @DisplayName("parses an incremental text delta out of one streamed chunk")
    void parsesStreamChunk() {
        String chunk = """
                {"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"}}]}""";
        assertThat(adapter.parseStreamChunk(chunk)).isEqualTo("Hello");
    }

    @Test
    @DisplayName("a stream chunk carrying only usageMetadata (end of stream) yields no text, not an error")
    void streamChunkWithOnlyUsageYieldsEmptyText() {
        String chunk = """
                {"usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":8,"totalTokenCount":20}}""";
        assertThat(adapter.parseStreamChunk(chunk)).isEmpty();
    }
}

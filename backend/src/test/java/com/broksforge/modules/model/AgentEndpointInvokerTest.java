package com.broksforge.modules.model;

import com.broksforge.common.security.OutboundUrlGuard;
import com.broksforge.config.properties.ModelInvocationProperties;
import com.broksforge.config.properties.ProviderDefaultsProperties;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.model.adapter.AnthropicAdapter;
import com.broksforge.modules.model.adapter.GoogleAiStudioAdapter;
import com.broksforge.modules.model.adapter.GroqAdapter;
import com.broksforge.modules.model.adapter.OllamaAdapter;
import com.broksforge.modules.model.adapter.OpenAiAdapter;
import com.broksforge.modules.model.adapter.OpenRouterAdapter;
import com.broksforge.modules.model.adapter.ProviderAdapterRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the exact bug reported against real hosted-provider agents (Groq/OpenAI/OpenRouter,
 * all OpenAI-compatible {@code /chat/completions} APIs): the invoker was sending its generic
 * {@code {"input": ..., "parameters": {...}}} envelope, which those providers reject with HTTP 400
 * because there is no {@code messages} field. Uses a plain JDK {@link HttpServer} (no new test
 * dependency) so the exact outbound JSON can be captured and asserted on.
 */
@DisplayName("AgentEndpointInvoker — provider-aware request construction")
class AgentEndpointInvokerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AgentEndpointInvoker invoker() {
        ProviderAdapterRegistry registry = new ProviderAdapterRegistry(List.of(
                new OpenAiAdapter(), new GroqAdapter(), new OpenRouterAdapter(),
                new AnthropicAdapter(), new OllamaAdapter(), new GoogleAiStudioAdapter()));
        return new AgentEndpointInvoker(new OutboundUrlGuard(),
                new ModelInvocationProperties(5000, true, 100_000),
                new ProviderDefaultsProperties(Map.of(), 1024), registry, new ObjectMapper());
    }

    private ModelInvocationRequest request(String endpointUrl, Map<String, Object> parameters) {
        return request(endpointUrl, parameters, LlmProvider.GROQ, "llama-3.1-8b-instant");
    }

    private ModelInvocationRequest request(String endpointUrl, Map<String, Object> parameters,
                                            LlmProvider provider, String model) {
        return new ModelInvocationRequest(UUID.randomUUID(), UUID.randomUUID(), provider,
                model, "What is the refund policy?", parameters,
                new ModelTarget(endpointUrl, Map.of("Authorization", "Bearer sk-test-secret")));
    }

    private HttpServer startServer(int status, String responseBody, AtomicReference<String> capturedRequestBody)
            throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", exchange -> respond(exchange, status, responseBody, capturedRequestBody));
        srv.start();
        return srv;
    }

    private void respond(HttpExchange exchange, int status, String responseBody,
                         AtomicReference<String> capturedRequestBody) throws IOException {
        capturedRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    @DisplayName("a /chat/completions endpoint gets a real OpenAI-style messages request, not the generic envelope")
    void buildsOpenAiCompatiblePayloadForChatCompletions() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(200,
                "{\"choices\":[{\"message\":{\"content\":\"We offer a 30-day refund.\"}}],"
                        + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8,\"total_tokens\":20}}",
                captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions";

        ModelInvocationResult result = invoker().invoke(request(url, Map.of("temperature", 0.2, "max_tokens", 256)));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("We offer a 30-day refund.");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.totalTokens()).isEqualTo(20);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sent = mapper.readTree(captured.get());
        assertThat(sent.has("input")).as("must not send the generic 'input' field to a real provider").isFalse();
        assertThat(sent.has("parameters")).as("generation params must be top-level, not nested").isFalse();
        assertThat(sent.get("model").asText()).isEqualTo("llama-3.1-8b-instant");
        assertThat(sent.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(256);
        JsonNode messages = sent.get("messages");
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("What is the refund policy?");
    }

    @Test
    @DisplayName("applies a sensible default max_tokens when the caller specified none, instead of leaving it "
            + "to the provider's own (potentially huge) default")
    void appliesDefaultMaxTokensWhenCallerSpecifiedNone() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions";

        invoker().invoke(request(url, Map.of()));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sent = mapper.readTree(captured.get());
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    @DisplayName("never overrides an explicit max_tokens (or max_completion_tokens) the caller configured")
    void doesNotOverrideExplicitMaxTokens() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions";

        invoker().invoke(request(url, Map.of("max_tokens", 4096)));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sent = mapper.readTree(captured.get());
        assertThat(sent.get("max_tokens").asInt()).isEqualTo(4096);
    }

    @Test
    @DisplayName("a plain custom-REST agent endpoint keeps the original generic envelope (backward compatible)")
    void keepsGenericEnvelopeForCustomEndpoints() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(200, "{\"output\":\"hi\"}", captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/agent/run";

        ModelInvocationResult result = invoker().invoke(request(url, Map.of("temperature", 0.2)));

        assertThat(result.success()).isTrue();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode sent = mapper.readTree(captured.get());
        assertThat(sent.get("input").asText()).isEqualTo("What is the refund policy?");
        assertThat(sent.has("parameters")).isTrue();
        assertThat(sent.has("messages")).isFalse();
    }

    @Test
    @DisplayName("a provider HTTP 400 surfaces the real error message, not just the status code")
    void surfacesProviderErrorBodyOn400() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(400,
                "{\"error\":{\"message\":\"'messages' is a required property\","
                        + "\"type\":\"invalid_request_error\",\"code\":\"missing_field\"}}",
                captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions";

        ModelInvocationResult result = invoker().invoke(request(url, Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(400);
        assertThat(result.error())
                .contains("'messages' is a required property")
                .contains("type=invalid_request_error")
                .contains("code=missing_field");
    }

    @Test
    @DisplayName("a Google AI Studio endpoint substitutes the requested model into the URL and gets a "
            + "contents/generationConfig request, not the generic envelope")
    void buildsGeminiPayloadAndSubstitutesModelInUrl() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().toString());
            respond(exchange, 200,
                    "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"We offer a 30-day refund.\"}]},"
                            + "\"finishReason\":\"STOP\"}],"
                            + "\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":8,"
                            + "\"totalTokenCount\":20}}",
                    captured);
        });
        server.start();
        // The stored endpoint (as if configured with a placeholder/default model) — the adapter must
        // rewrite this to the actually-requested model before sending.
        String storedUrl = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/v1beta/models/gemini-2.5-flash:generateContent";

        ModelInvocationResult result = invoker().invoke(
                request(storedUrl, Map.of("temperature", 0.2), LlmProvider.GOOGLE_GEMINI, "gemini-2.5-pro"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("We offer a 30-day refund.");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.totalTokens()).isEqualTo(20);
        assertThat(capturedPath.get()).isEqualTo("/v1beta/models/gemini-2.5-pro:generateContent");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode sent = mapper.readTree(captured.get());
        assertThat(sent.has("contents")).isTrue();
        assertThat(sent.has("input")).as("must not send the generic envelope to Gemini").isFalse();
        assertThat(sent.get("generationConfig").get("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("a Google AI Studio safety block (HTTP 200, empty candidate) is reported as a failure, "
            + "not a successful empty output")
    void reportsGeminiSafetyBlockAsFailure() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        server = startServer(200,
                "{\"candidates\":[{\"finishReason\":\"SAFETY\","
                        + "\"safetyRatings\":[{\"category\":\"HARM_CATEGORY_DANGEROUS_CONTENT\","
                        + "\"probability\":\"HIGH\"}]}]}",
                captured);
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/v1beta/models/gemini-2.5-flash:generateContent";

        ModelInvocationResult result = invoker().invoke(
                request(url, Map.of(), LlmProvider.GOOGLE_GEMINI, "gemini-2.5-flash"));

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.error()).contains("SAFETY").contains("HARM_CATEGORY_DANGEROUS_CONTENT");
    }
}

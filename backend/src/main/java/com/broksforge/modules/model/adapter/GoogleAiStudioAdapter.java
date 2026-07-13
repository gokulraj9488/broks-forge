package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google AI Studio's Gemini API ({@code /v1beta/models/{model}:generateContent}) — supports any
 * current or future Gemini model (gemini-2.5-flash, gemini-2.5-pro, ...) since the model name is
 * never hardcoded, only substituted into the URL. Materially different from every other adapter
 * in three ways:
 *
 * <ol>
 *   <li><b>The model lives in the URL path, not the request body.</b> {@link #resolveInvocationUrl}
 *       rewrites {@code .../models/&lt;anything&gt;:generateContent} to the caller's actual
 *       {@code model}, so a job/profile model override still takes effect even though this
 *       provider's wire convention differs from every message-body-based provider.</li>
 *   <li><b>A 2xx response can still be a safety block.</b> Gemini returns HTTP 200 with an empty
 *       candidate (no {@code content.parts}) and {@code finishReason} of {@code SAFETY} or
 *       {@code RECITATION} when the model refused to answer — {@link #parseSuccess} detects this
 *       and reports it via {@link ParsedInvocation#blockedReason()} instead of silently returning
 *       blank output that would be misread as a quality failure rather than a policy block.</li>
 *   <li><b>Generation parameters nest under {@code generationConfig}</b> with Gemini's own field
 *       names ({@code maxOutputTokens}, not {@code max_tokens}) — {@link #buildPayload} accepts
 *       either the OpenAI-style key (for callers that don't know which provider they're
 *       targeting) or Gemini's own, and never sends both.</li>
 * </ol>
 *
 * <p>Streaming ({@code :streamGenerateContent}) is supported at the protocol level —
 * {@link #streamingInvocationUrl} builds the streaming route and {@link #parseStreamChunk} parses
 * one server-sent chunk — but {@code AgentEndpointInvoker}'s batch evaluation call path
 * intentionally keeps using the non-streaming route: a background evaluation row has no live
 * viewer to stream tokens to, and wiring incremental consumption into the synchronous HTTP client
 * used by every other provider would be an invocation-pipeline redesign, not a provider adapter.
 * The chunk parser is ready for a future direct-invocation/chat feature to use directly.</p>
 */
@Component
public class GoogleAiStudioAdapter implements ProviderAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_DETAIL_CHARS = 700;
    private static final Pattern MODEL_SEGMENT =
            Pattern.compile("(/models/)([^/:]+)(:(?:generateContent|streamGenerateContent))");

    /** Gemini finish reasons that mean the response was blocked/refused, not merely truncated. */
    private static final List<String> BLOCKING_FINISH_REASONS = List.of("SAFETY", "RECITATION", "BLOCKLIST",
            "PROHIBITED_CONTENT", "SPII");

    @Override
    public LlmProvider providerType() {
        return LlmProvider.GOOGLE_GEMINI;
    }

    @Override
    public boolean supportsEndpoint(String endpointUrl) {
        if (endpointUrl == null) {
            return false;
        }
        try {
            String path = URI.create(endpointUrl).getPath();
            if (path == null) {
                return false;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith(":generatecontent") || lower.endsWith(":streamgeneratecontent");
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String resolveInvocationUrl(String endpointUrl, String model) {
        if (endpointUrl == null || model == null || model.isBlank()) {
            return endpointUrl;
        }
        Matcher matcher = MODEL_SEGMENT.matcher(endpointUrl);
        if (!matcher.find()) {
            return endpointUrl;
        }
        return matcher.replaceFirst("$1" + Matcher.quoteReplacement(model.trim()) + "$3");
    }

    /** Google AI Studio expects the key in {@code x-goog-api-key}, not an Authorization header. */
    @Override
    public Map<String, String> buildAuthHeaders(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? Map.of() : Map.of("x-goog-api-key", apiKey);
    }

    /** The streaming variant of {@code endpointUrl} — swaps {@code :generateContent} for {@code :streamGenerateContent}. */
    public String streamingInvocationUrl(String endpointUrl) {
        if (endpointUrl == null) {
            return null;
        }
        return endpointUrl.replaceFirst("(?i):generateContent$", ":streamGenerateContent");
    }

    @Override
    public Map<String, Object> buildPayload(String model, String input, Map<String, Object> parameters,
                                            int defaultMaxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input)))));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            generationConfig.put(geminiParamKey(entry.getKey()), entry.getValue());
        }
        if (!generationConfig.containsKey("maxOutputTokens")) {
            generationConfig.put("maxOutputTokens", defaultMaxTokens);
        }
        payload.put("generationConfig", generationConfig);
        return payload;
    }

    /** Maps the OpenAI-style parameter name callers may use onto Gemini's own field name; passes through unknown keys. */
    private String geminiParamKey(String key) {
        return switch (key) {
            case "max_tokens", "max_completion_tokens" -> "maxOutputTokens";
            case "top_p" -> "topP";
            case "top_k" -> "topK";
            case "stop", "stop_sequences" -> "stopSequences";
            default -> key;
        };
    }

    @Override
    public ParsedInvocation parseSuccess(String body) {
        if (body == null || body.isBlank()) {
            return new ParsedInvocation("", null, null, null, null, null);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode candidates = root.get("candidates");
            JsonNode first = (candidates != null && candidates.isArray() && !candidates.isEmpty())
                    ? candidates.get(0) : null;

            String finishReason = first != null && first.hasNonNull("finishReason")
                    ? first.get("finishReason").asText() : null;
            String output = extractText(first);

            JsonNode usage = root.get("usageMetadata");
            Integer promptTokens = intField(usage, "promptTokenCount");
            Integer completionTokens = intField(usage, "candidatesTokenCount");
            Integer totalTokens = intField(usage, "totalTokenCount");

            if (!output.isEmpty() && finishReason == null) {
                return new ParsedInvocation(output, promptTokens, completionTokens, totalTokens, null, null);
            }
            if (output.isEmpty() && finishReason != null && BLOCKING_FINISH_REASONS.contains(finishReason)) {
                String detail = safetyDetail(first);
                return new ParsedInvocation("", promptTokens, completionTokens, totalTokens, null,
                        "Response blocked by Google AI Studio (finishReason=%s)%s".formatted(finishReason,
                                detail.isEmpty() ? "" : ": " + detail));
            }
            if (output.isEmpty() && candidates == null) {
                // A prompt-level block (e.g. the input itself was blocked) has no candidates array at all.
                JsonNode promptFeedback = root.get("promptFeedback");
                String blockReason = promptFeedback != null && promptFeedback.hasNonNull("blockReason")
                        ? promptFeedback.get("blockReason").asText() : "UNKNOWN";
                return new ParsedInvocation("", promptTokens, completionTokens, totalTokens, null,
                        "Prompt blocked by Google AI Studio (blockReason=%s)".formatted(blockReason));
            }
            return new ParsedInvocation(output, promptTokens, completionTokens, totalTokens, null, null);
        } catch (Exception notJson) {
            return new ParsedInvocation(body, null, null, null, null, null);
        }
    }

    private String extractText(JsonNode candidate) {
        if (candidate == null) {
            return "";
        }
        JsonNode content = candidate.get("content");
        JsonNode parts = content != null ? content.get("parts") : null;
        if (parts == null || !parts.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.hasNonNull("text")) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }

    private String safetyDetail(JsonNode candidate) {
        if (candidate == null) {
            return "";
        }
        JsonNode ratings = candidate.get("safetyRatings");
        if (ratings == null || !ratings.isArray()) {
            return "";
        }
        List<String> flagged = new java.util.ArrayList<>();
        for (JsonNode rating : ratings) {
            if (rating.hasNonNull("probability")
                    && List.of("MEDIUM", "HIGH").contains(rating.get("probability").asText())
                    && rating.hasNonNull("category")) {
                flagged.add(rating.get("category").asText());
            }
        }
        return String.join(", ", flagged);
    }

    @Override
    public String parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode error = root.get("error");
            if (error == null || !error.isObject()) {
                return null;
            }
            String message = error.hasNonNull("message") ? error.get("message").asText() : null;
            String status = error.hasNonNull("status") ? error.get("status").asText() : null;
            if (message == null) {
                return null;
            }
            String result = status != null ? message + " (status=" + status + ")" : message;
            return truncate(result);
        } catch (Exception notJson) {
            return null;
        }
    }

    /**
     * Parses one server-sent-event data chunk from {@code :streamGenerateContent} (each chunk is
     * shaped like a partial version of the full response — a {@code candidates[0].content.parts[].text}
     * fragment) into the incremental text it carries. Returns an empty string for a chunk with no
     * text (e.g. a chunk that only carries {@code usageMetadata} at the end of the stream).
     */
    public String parseStreamChunk(String chunkJson) {
        if (chunkJson == null || chunkJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(chunkJson);
            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                return "";
            }
            return extractText(candidates.get(0));
        } catch (Exception notJson) {
            return "";
        }
    }

    private Integer intField(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(key);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private String truncate(String value) {
        return value.length() <= MAX_ERROR_DETAIL_CHARS ? value : value.substring(0, MAX_ERROR_DETAIL_CHARS);
    }
}

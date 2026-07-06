package com.broksforge.modules.agent.service;

import org.springframework.http.client.ClientHttpResponse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small helpers shared by the outbound probers ({@link CredentialConnectionTester} and
 * {@link AgentHealthCheckExecutor}) for safe debug logging: masking secret-bearing headers and
 * reading a bounded snippet of a response body. Never logs a raw secret.
 */
final class ProbeSupport {

    private ProbeSupport() {
    }

    /** Returns a copy of the headers with any secret-bearing value masked (safe to log). */
    static Map<String, String> maskedHeaders(Map<String, String> headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> masked.put(name, isSensitive(name) ? mask(value) : value));
        }
        return masked;
    }

    private static boolean isSensitive(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("authorization")
                || n.contains("api-key")
                || n.contains("apikey")
                || n.contains("api_key")
                || n.contains("token")
                || n.contains("secret");
    }

    /**
     * Masks a secret value while preserving a leading scheme word (e.g. {@code Bearer}) and the last
     * four characters, so a log reader can confirm the prefix and correlate the key without exposing it.
     */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        int space = value.indexOf(' ');
        String scheme = "";
        String secret = value;
        if (space > 0) {
            scheme = value.substring(0, space + 1); // includes the trailing space, e.g. "Bearer "
            secret = value.substring(space + 1);
        }
        String tail = secret.length() > 4 ? secret.substring(secret.length() - 4) : "";
        return scheme + "****" + tail;
    }

    /** Reads at most {@code max} bytes of the response body as UTF-8 for debug logging; never throws. */
    static String bodySnippet(ClientHttpResponse response, int max) {
        try (InputStream body = response.getBody()) {
            byte[] bytes = body.readNBytes(max);
            String text = new String(bytes, StandardCharsets.UTF_8).strip();
            return text.replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "";
        }
    }
}

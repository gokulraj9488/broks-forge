package com.broksforge.modules.model.judge;

/**
 * {@code httpStatus} is the raw status the provider returned, when there was one (null for a
 * failure that never got an HTTP response — timeout, connection refused, network-policy block,
 * or a config problem caught before the call was made). Callers classify it via
 * {@code MetricExecutionStatus.classify(httpStatus, error)} to distinguish a transport/auth
 * failure from an actual low similarity score.
 */
public record EmbeddingResult(boolean ok, float[] vector, String error, Integer httpStatus) {

    public static EmbeddingResult of(float[] vector) {
        return new EmbeddingResult(true, vector, null, null);
    }

    public static EmbeddingResult error(String message) {
        return new EmbeddingResult(false, null, message, null);
    }

    public static EmbeddingResult error(String message, Integer httpStatus) {
        return new EmbeddingResult(false, null, message, httpStatus);
    }
}

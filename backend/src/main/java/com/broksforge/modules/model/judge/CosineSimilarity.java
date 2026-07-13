package com.broksforge.modules.model.judge;

/** Pure cosine-similarity math for two equal-length embedding vectors. */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /** Returns {@code null} if the vectors are null, empty, mismatched in length, or either is a zero vector. */
    public static Double of(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return null;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return null;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

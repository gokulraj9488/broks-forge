package com.broksforge.modules.model.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        Double similarity = CosineSimilarity.of(new float[]{1f, 2f, 3f}, new float[]{1f, 2f, 3f});
        assertThat(similarity).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        Double similarity = CosineSimilarity.of(new float[]{1f, 0f}, new float[]{0f, 1f});
        assertThat(similarity).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void oppositeVectorsScoreNegativeOne() {
        Double similarity = CosineSimilarity.of(new float[]{1f, 0f}, new float[]{-1f, 0f});
        assertThat(similarity).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void returnsNullForMismatchedLengths() {
        assertThat(CosineSimilarity.of(new float[]{1f, 2f}, new float[]{1f})).isNull();
    }

    @Test
    void returnsNullForNullOrEmptyVectors() {
        assertThat(CosineSimilarity.of(null, new float[]{1f})).isNull();
        assertThat(CosineSimilarity.of(new float[]{}, new float[]{})).isNull();
    }

    @Test
    void returnsNullForZeroVector() {
        assertThat(CosineSimilarity.of(new float[]{0f, 0f}, new float[]{1f, 1f})).isNull();
    }
}

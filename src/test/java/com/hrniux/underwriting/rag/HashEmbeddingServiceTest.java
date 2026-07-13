package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HashEmbeddingServiceTest {

    private final HashEmbeddingService service = new HashEmbeddingService(64);

    @Test
    void returnsTheSameNormalizedVectorForTheSameText() {
        double[] first = service.embed("暴雨 仓库 drainage");
        double[] second = service.embed("暴雨 仓库 drainage");

        assertThat(first).containsExactly(second);
        assertThat(l2Norm(first)).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    void returnsAZeroVectorForBlankText() {
        assertThat(service.embed("   ")).containsOnly(0.0);
    }

    private double l2Norm(double[] vector) {
        double squared = java.util.Arrays.stream(vector).map(value -> value * value).sum();
        return Math.sqrt(squared);
    }
}

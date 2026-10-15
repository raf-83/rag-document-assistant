package com.aidocumentvault.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiEmbeddingPropertiesTest {

    @Test
    void shouldCreateValidProperties() {
        // given
        String model = "text-embedding-3-small";
        int dimensions = 1536;
        int batchSize = 32;

        // when
        AiEmbeddingProperties properties = new AiEmbeddingProperties(
            model,
            dimensions,
            batchSize
        );

        // then
        assertThat(properties.model()).isEqualTo(model);
        assertThat(properties.dimensions()).isEqualTo(dimensions);
        assertThat(properties.batchSize()).isEqualTo(batchSize);
    }

    @Test
    void shouldRejectBlankModel() {
        assertThatThrownBy(() -> new AiEmbeddingProperties(
            " ",
            1536,
            32
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding model must not be blank");
    }

    @Test
    void shouldRejectUnsupportedDimensions() {
        assertThatThrownBy(() -> new AiEmbeddingProperties(
            "text-embedding-3-small",
            3072,
            32
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding dimensions must be 1536");
    }

    @Test
    void shouldRejectTooSmallBatchSize() {
        assertThatThrownBy(() -> new AiEmbeddingProperties(
            "text-embedding-3-small",
            1536,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding batchSize must be between 1 and 100");
    }

    @Test
    void shouldRejectTooLargeBatchSize() {
        assertThatThrownBy(() -> new AiEmbeddingProperties(
            "text-embedding-3-small",
            1536,
            101
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding batchSize must be between 1 and 100");
    }
}

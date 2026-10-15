package com.aidocumentvault.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiHybridSearchPropertiesTest {

    @Test
    void shouldCalculateCandidateLimitUsingMultiplierAndChunkLimit() {
        // given
        AiHybridSearchProperties properties = new AiHybridSearchProperties(
            60,
            0.60,
            0.40,
            3,
            150,
            3,
            0.15
        );

        // when
        int candidateLimit = properties.candidateLimitFor(5);

        // then
        assertThat(candidateLimit).isEqualTo(45);
    }

    @Test
    void shouldNotExceedMaxCandidateLimit() {
        // given
        AiHybridSearchProperties properties = new AiHybridSearchProperties(
            60,
            0.60,
            0.40,
            3,
            150,
            3,
            0.15
        );

        // when
        int candidateLimit = properties.candidateLimitFor(100);

        // then
        assertThat(candidateLimit).isEqualTo(150);
    }

    @Test
    void shouldRejectInvalidFinalLimit() {
        // given
        AiHybridSearchProperties properties = new AiHybridSearchProperties(
            60,
            0.60,
            0.40,
            3,
            150,
            3,
            0.15
        );

        // when / then
        assertThatThrownBy(() -> properties.candidateLimitFor(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Final document limit must be at least 1");
    }

    @Test
    void shouldCalculateCandidateLimitForFinalChunks() {
        // given
        AiHybridSearchProperties properties =
            new AiHybridSearchProperties(
                60,
                0.60,
                0.40,
                3,
                150,
                3,
                0.15
            );

        // when
        int candidateLimit =
            properties.candidateLimitForChunks(5);

        // then
        assertThat(candidateLimit).isEqualTo(15);
    }

    @Test
    void shouldCapCandidateLimitForChunks() {
        // given
        AiHybridSearchProperties properties =
            new AiHybridSearchProperties(
                60,
                0.60,
                0.40,
                20,
                150,
                3,
                0.15
            );

        // when
        int candidateLimit =
            properties.candidateLimitForChunks(20);

        // then
        assertThat(candidateLimit).isEqualTo(150);
    }

    @Test
    void shouldRejectInvalidFinalChunkLimit() {
        // given
        AiHybridSearchProperties properties =
            new AiHybridSearchProperties(
                60,
                0.60,
                0.40,
                3,
                150,
                3,
                0.15
            );

        // when / then
        assertThatThrownBy(() ->
            properties.candidateLimitForChunks(0)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Final chunk limit must be at least 1"
            );
    }
}

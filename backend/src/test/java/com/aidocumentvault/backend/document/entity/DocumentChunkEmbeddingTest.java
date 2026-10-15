package com.aidocumentvault.backend.document.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class DocumentChunkEmbeddingTest {

    @Test
    void shouldCreateChunkEmbedding() {
        // given
        DocumentChunk documentChunk = mock(DocumentChunk.class);
        float[] embedding = createEmbedding(1536);

        // when
        DocumentChunkEmbedding chunkEmbedding = new DocumentChunkEmbedding(
            documentChunk,
            embedding,
            "text-embedding-3-small",
            1536
        );

        // then
        assertThat(chunkEmbedding.getDocumentChunk()).isEqualTo(documentChunk);
        assertThat(chunkEmbedding.getEmbedding()).hasSize(1536);
        assertThat(chunkEmbedding.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(chunkEmbedding.getDimensions()).isEqualTo(1536);
        assertThat(chunkEmbedding.getGeneratedAt()).isNotNull();

        verify(documentChunk).attachEmbedding(chunkEmbedding);
    }

    @Test
    void shouldDefensivelyCopyEmbeddingArray() {
        // given
        DocumentChunk documentChunk = mock(DocumentChunk.class);
        float[] embedding = createEmbedding(1536);

        // when
        DocumentChunkEmbedding chunkEmbedding = new DocumentChunkEmbedding(
            documentChunk,
            embedding,
            "text-embedding-3-small",
            1536
        );

        embedding[0] = 999.0f;

        // then
        assertThat(chunkEmbedding.getEmbedding()[0]).isNotEqualTo(999.0f);
    }

    @Test
    void shouldRejectNullDocumentChunk() {
        assertThatThrownBy(() -> new DocumentChunkEmbedding(
            null,
            createEmbedding(1536),
            "text-embedding-3-small",
            1536
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("documentChunk must not be null");
    }

    @Test
    void shouldRejectEmptyEmbedding() {
        DocumentChunk documentChunk = mock(DocumentChunk.class);

        assertThatThrownBy(() -> new DocumentChunkEmbedding(
            documentChunk,
            new float[0],
            "text-embedding-3-small",
            1536
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding must not be empty");
    }

    @Test
    void shouldRejectDimensionsDifferentThanSupportedSchema() {
        DocumentChunk documentChunk = mock(DocumentChunk.class);

        assertThatThrownBy(() -> new DocumentChunkEmbedding(
            documentChunk,
            createEmbedding(1536),
            "text-embedding-3-small",
            3072
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dimensions must be 1536");
    }

    @Test
    void shouldRejectEmbeddingLengthDifferentThanDimensions() {
        DocumentChunk documentChunk = mock(DocumentChunk.class);

        assertThatThrownBy(() -> new DocumentChunkEmbedding(
            documentChunk,
            createEmbedding(100),
            "text-embedding-3-small",
            1536
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("embedding length must match dimensions");
    }

    @Test
    void shouldRejectBlankModel() {
        DocumentChunk documentChunk = mock(DocumentChunk.class);

        assertThatThrownBy(() -> new DocumentChunkEmbedding(
            documentChunk,
            createEmbedding(1536),
            " ",
            1536
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("model must not be blank");
    }

    private float[] createEmbedding(int dimensions) {
        float[] embedding = new float[dimensions];

        for (int i = 0; i < dimensions; i++) {
            embedding[i] = 0.001f * i;
        }

        return embedding;
    }
}

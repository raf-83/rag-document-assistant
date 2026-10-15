package com.aidocumentvault.backend.ai.retrieval.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.EmbeddingClient;
import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.ai.search.persistence.SemanticSearchRepository;
import com.aidocumentvault.backend.ai.search.persistence.SemanticSearchResultRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticChunkRetrievalServiceImplTest {

    private static final String MODEL =
        "text-embedding-3-small";

    private static final int DIMENSIONS = 1536;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private SemanticSearchRepository semanticSearchRepository;

    @Mock
    private SemanticSearchResultRow resultRow;

    private SemanticChunkRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        AiEmbeddingProperties properties =
            new AiEmbeddingProperties(
                MODEL,
                DIMENSIONS,
                32
            );

        service = new SemanticChunkRetrievalServiceImpl(
            embeddingClient,
            properties,
            semanticSearchRepository
        );
    }

    @Test
    void shouldGenerateEmbeddingAndReturnFullChunk() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID documentTypeId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                ownerId,
                documentTypeId,
                null,
                null,
                Set.of(documentId),
                15
            );

        when(
            embeddingClient.embed(
                List.of("prawo do zasiłku")
            )
        ).thenReturn(
            List.of(
                new EmbeddingVector(queryEmbedding())
            )
        );

        when(
            semanticSearchRepository.findNearestChunks(
                anyString(),
                eq(ownerId),
                eq(MODEL),
                eq(DIMENSIONS),
                eq(documentTypeId),
                eq(null),
                eq(null),
                eq(false),
                eq(Set.of(documentId)),
                eq(15)
            )
        ).thenReturn(List.of(resultRow));

        String fullChunkText =
            "Pełny tekst chunku zawierający informację "
                + "o prawie do zasiłku dla osoby bezrobotnej.";

        Instant createdAt =
            Instant.parse("2026-07-01T10:00:00Z");

        when(resultRow.getDocumentId())
            .thenReturn(documentId);
        when(resultRow.getDocumentTitle())
            .thenReturn("Decyzja o zasiłku");
        when(resultRow.getDocumentTypeId())
            .thenReturn(documentTypeId);
        when(resultRow.getDocumentTypeName())
            .thenReturn("Dokument urzędowy");
        when(resultRow.getChunkId())
            .thenReturn(chunkId);
        when(resultRow.getChunkIndex())
            .thenReturn(2);
        when(resultRow.getText())
            .thenReturn(fullChunkText);
        when(resultRow.getStartCharIndex())
            .thenReturn(1200);
        when(resultRow.getEndCharIndex())
            .thenReturn(1285);
        when(resultRow.getScore())
            .thenReturn(0.72);
        when(resultRow.getDocumentCreatedAt())
            .thenReturn(createdAt);

        // when
        List<SemanticRetrievedChunk> results =
            service.retrieve(criteria);

        // then
        assertThat(results)
            .singleElement()
            .satisfies(result -> {
                assertThat(result.documentId())
                    .isEqualTo(documentId);
                assertThat(result.chunkId())
                    .isEqualTo(chunkId);
                assertThat(result.chunkIndex())
                    .isEqualTo(2);
                assertThat(result.text())
                    .isEqualTo(fullChunkText);
                assertThat(result.startCharIndex())
                    .isEqualTo(1200);
                assertThat(result.endCharIndex())
                    .isEqualTo(1285);
                assertThat(result.score())
                    .isEqualTo(0.72);
                assertThat(result.documentCreatedAt())
                    .isEqualTo(createdAt);
            });

        verify(embeddingClient)
            .embed(List.of("prawo do zasiłku"));

        ArgumentCaptor<String> vectorCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(semanticSearchRepository)
            .findNearestChunks(
                vectorCaptor.capture(),
                eq(ownerId),
                eq(MODEL),
                eq(DIMENSIONS),
                eq(documentTypeId),
                eq(null),
                eq(null),
                eq(false),
                eq(Set.of(documentId)),
                eq(15)
            );

        assertThat(vectorCaptor.getValue())
            .startsWith("[0.0,0.001,0.002")
            .endsWith("1.535]");
    }

    private static float[] queryEmbedding() {
        float[] values = new float[DIMENSIONS];

        for (int index = 0; index < values.length; index++) {
            values[index] = index / 1000.0f;
        }

        return values;
    }
}

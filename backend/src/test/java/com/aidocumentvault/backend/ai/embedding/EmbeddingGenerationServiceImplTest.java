package com.aidocumentvault.backend.ai.embedding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.service.DocumentChunkEmbeddingPersistenceService;
import com.aidocumentvault.backend.ai.embedding.service.EmbeddingGenerationServiceImpl;
import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EmbeddingGenerationServiceImplTest {

    private final DocumentChunkRepository documentChunkRepository =
        mock(DocumentChunkRepository.class);

    private final EmbeddingClient embeddingClient =
        mock(EmbeddingClient.class);

    private final DocumentChunkEmbeddingPersistenceService embeddingPersistenceService =
        mock(DocumentChunkEmbeddingPersistenceService.class);

    private final AiEmbeddingProperties aiEmbeddingProperties =
        new AiEmbeddingProperties("text-embedding-3-small", 1536, 2);

    private final EmbeddingGenerationServiceImpl embeddingGenerationService =
        new EmbeddingGenerationServiceImpl(
            documentChunkRepository,
            aiEmbeddingProperties,
            embeddingClient,
            embeddingPersistenceService
        );

    @Test
    void shouldDoNothingWhenThereAreNoChunksToEmbed() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of());

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        verify(documentChunkRepository).findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        );

        verify(embeddingClient, never()).embed(any());
        verify(embeddingPersistenceService, never()).markAsInProgress(any());
        verify(embeddingPersistenceService, never()).saveEmbeddingsAndMarkAsReady(
            any(),
            any(),
            any(),
            any(Integer.class)
        );
        verify(embeddingPersistenceService, never()).markAsFailed(any());
    }

    @Test
    void shouldGenerateEmbeddingsForChunks() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk firstChunk = chunk(UUID.randomUUID(), "Pierwszy chunk");
        DocumentChunk secondChunk = chunk(UUID.randomUUID(), "Drugi chunk");

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of(firstChunk, secondChunk));

        List<EmbeddingVector> vectors = List.of(
            vector(1536),
            vector(1536)
        );

        when(embeddingClient.embed(List.of("Pierwszy chunk", "Drugi chunk")))
            .thenReturn(vectors);

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        InOrder inOrder = inOrder(
            embeddingPersistenceService,
            embeddingClient
        );

        List<UUID> chunkIds = List.of(
            firstChunk.getId(),
            secondChunk.getId()
        );

        inOrder.verify(embeddingPersistenceService).markAsInProgress(chunkIds);
        inOrder.verify(embeddingClient).embed(List.of("Pierwszy chunk", "Drugi chunk"));
        inOrder.verify(embeddingPersistenceService).saveEmbeddingsAndMarkAsReady(
            List.of(firstChunk, secondChunk),
            vectors,
            "text-embedding-3-small",
            1536
        );

        verify(embeddingPersistenceService, never()).markAsFailed(any());
    }

    @Test
    void shouldProcessChunksInBatches() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk firstChunk = chunk(UUID.randomUUID(), "Chunk 1");
        DocumentChunk secondChunk = chunk(UUID.randomUUID(), "Chunk 2");
        DocumentChunk thirdChunk = chunk(UUID.randomUUID(), "Chunk 3");

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of(firstChunk, secondChunk, thirdChunk));

        when(embeddingClient.embed(List.of("Chunk 1", "Chunk 2")))
            .thenReturn(List.of(vector(1536), vector(1536)));

        when(embeddingClient.embed(List.of("Chunk 3")))
            .thenReturn(List.of(vector(1536)));

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        verify(embeddingPersistenceService).markAsInProgress(List.of(
            firstChunk.getId(),
            secondChunk.getId()
        ));

        verify(embeddingPersistenceService).markAsInProgress(List.of(
            thirdChunk.getId()
        ));

        verify(embeddingClient).embed(List.of("Chunk 1", "Chunk 2"));
        verify(embeddingClient).embed(List.of("Chunk 3"));
    }

    @Test
    void shouldMarkBatchAsFailedWhenEmbeddingClientThrowsException() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk firstChunk = chunk(UUID.randomUUID(), "Pierwszy chunk");
        DocumentChunk secondChunk = chunk(UUID.randomUUID(), "Drugi chunk");

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of(firstChunk, secondChunk));

        when(embeddingClient.embed(List.of("Pierwszy chunk", "Drugi chunk")))
            .thenThrow(new IllegalStateException("Fake embedding provider failed"));

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        verify(embeddingPersistenceService).markAsInProgress(List.of(
            firstChunk.getId(),
            secondChunk.getId()
        ));

        verify(embeddingPersistenceService).markAsFailed(List.of(
            firstChunk.getId(),
            secondChunk.getId()
        ));

        verify(embeddingPersistenceService, never()).saveEmbeddingsAndMarkAsReady(
            any(),
            any(),
            any(),
            any(Integer.class)
        );
    }

    @Test
    void shouldMarkBatchAsFailedWhenEmbeddingCountDoesNotMatchChunkCount() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk firstChunk = chunk(UUID.randomUUID(), "Pierwszy chunk");
        DocumentChunk secondChunk = chunk(UUID.randomUUID(), "Drugi chunk");

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of(firstChunk, secondChunk));

        when(embeddingClient.embed(List.of("Pierwszy chunk", "Drugi chunk")))
            .thenReturn(List.of(vector(1536)));

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        verify(embeddingPersistenceService).markAsFailed(List.of(
            firstChunk.getId(),
            secondChunk.getId()
        ));

        verify(embeddingPersistenceService, never()).saveEmbeddingsAndMarkAsReady(
            any(),
            any(),
            any(),
            any(Integer.class)
        );
    }

    @Test
    void shouldMarkBatchAsFailedWhenEmbeddingDimensionsAreInvalid() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentContentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk firstChunk = chunk(UUID.randomUUID(), "Pierwszy chunk");

        when(documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            statusesToProcess()
        )).thenReturn(List.of(firstChunk));

        when(embeddingClient.embed(List.of("Pierwszy chunk")))
            .thenReturn(List.of(vector(10)));

        // when
        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            documentId,
            documentContentId,
            userId
        );

        // then
        verify(embeddingPersistenceService).markAsFailed(List.of(firstChunk.getId()));

        verify(embeddingPersistenceService, never()).saveEmbeddingsAndMarkAsReady(
            any(),
            any(),
            any(),
            any(Integer.class)
        );
    }

    private Set<EmbeddingStatus> statusesToProcess() {
        return EnumSet.of(EmbeddingStatus.NOT_CREATED);
    }

    private DocumentChunk chunk(UUID id, String text) {
        DocumentChunk chunk = mock(DocumentChunk.class);
        when(chunk.getId()).thenReturn(id);
        when(chunk.getText()).thenReturn(text);
        return chunk;
    }

    private EmbeddingVector vector(int dimensions) {
        float[] values = new float[dimensions];

        for (int i = 0; i < dimensions; i++) {
            values[i] = i * 0.001f;
        }

        return new EmbeddingVector(values);
    }
}

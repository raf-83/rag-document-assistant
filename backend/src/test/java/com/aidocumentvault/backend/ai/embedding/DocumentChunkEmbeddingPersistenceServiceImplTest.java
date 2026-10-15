package com.aidocumentvault.backend.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aidocumentvault.backend.ai.embedding.service.DocumentChunkEmbeddingPersistenceServiceImpl;
import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.DocumentChunkEmbedding;
import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
import com.aidocumentvault.backend.document.repository.DocumentChunkEmbeddingRepository;
import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentChunkEmbeddingPersistenceServiceImplTest {

    private final DocumentChunkRepository documentChunkRepository =
        mock(DocumentChunkRepository.class);

    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository =
        mock(DocumentChunkEmbeddingRepository.class);

    private final DocumentChunkEmbeddingPersistenceServiceImpl persistenceService =
        new DocumentChunkEmbeddingPersistenceServiceImpl(
            documentChunkRepository,
            documentChunkEmbeddingRepository
        );

    @Test
    void shouldMarkChunksAsInProgress() {
        // given
        List<UUID> chunkIds = List.of(
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        // when
        persistenceService.markAsInProgress(chunkIds);

        // then
        verify(documentChunkRepository).updateEmbeddingStatusByIdIn(
            chunkIds,
            EmbeddingStatus.IN_PROGRESS
        );
    }

    @Test
    void shouldMarkChunksAsFailed() {
        // given
        List<UUID> chunkIds = List.of(
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        // when
        persistenceService.markAsFailed(chunkIds);

        // then
        verify(documentChunkRepository).updateEmbeddingStatusByIdIn(
            chunkIds,
            EmbeddingStatus.FAILED
        );
    }

    @Test
    void shouldSaveEmbeddingsAndMarkChunksAsReady() {
        // given
        UUID firstChunkId = UUID.randomUUID();
        UUID secondChunkId = UUID.randomUUID();

        DocumentChunk firstDetachedChunk = chunk(firstChunkId);
        DocumentChunk secondDetachedChunk = chunk(secondChunkId);

        DocumentChunk firstManagedChunk = chunk(firstChunkId);
        DocumentChunk secondManagedChunk = chunk(secondChunkId);

        List<DocumentChunk> detachedChunks = List.of(
            firstDetachedChunk,
            secondDetachedChunk
        );

        List<EmbeddingVector> vectors = List.of(
            vector(1536),
            vector(1536)
        );

        when(documentChunkRepository.findAllById(List.of(firstChunkId, secondChunkId)))
            .thenReturn(List.of(firstManagedChunk, secondManagedChunk));

        when(documentChunkEmbeddingRepository.findAllByDocumentChunkIdIn(
            List.of(firstChunkId, secondChunkId)
        )).thenReturn(List.of());

        // when
        persistenceService.saveEmbeddingsAndMarkAsReady(
            detachedChunks,
            vectors,
            "text-embedding-3-small",
            1536
        );

        // then
        // then
        ArgumentCaptor<DocumentChunkEmbedding> captor =
            ArgumentCaptor.forClass(DocumentChunkEmbedding.class);

        verify(documentChunkEmbeddingRepository, times(2))
            .save(captor.capture());

        List<DocumentChunkEmbedding> savedEmbeddings = captor.getAllValues();

        assertThat(savedEmbeddings).hasSize(2);

        assertThat(savedEmbeddings)
            .extracting(DocumentChunkEmbedding::getModel)
            .containsOnly("text-embedding-3-small");

        verify(firstManagedChunk).attachEmbedding(any(DocumentChunkEmbedding.class));
        verify(secondManagedChunk).attachEmbedding(any(DocumentChunkEmbedding.class));

        verify(documentChunkRepository).flush();
        verify(documentChunkEmbeddingRepository).flush();
    }

    private DocumentChunk chunk(UUID id) {
        DocumentChunk chunk = mock(DocumentChunk.class);
        when(chunk.getId()).thenReturn(id);
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

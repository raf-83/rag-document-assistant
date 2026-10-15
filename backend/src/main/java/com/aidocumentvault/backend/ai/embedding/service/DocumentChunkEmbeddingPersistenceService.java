package com.aidocumentvault.backend.ai.embedding.service;

import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import com.aidocumentvault.backend.document.entity.DocumentChunk;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkEmbeddingPersistenceService {

    void markAsInProgress(List<UUID> chunkIds);

    void saveEmbeddingsAndMarkAsReady(
        List<DocumentChunk> chunks,
        List<EmbeddingVector> vectors,
        String model,
        int dimensions
    );

    void markAsFailed(List<UUID> chunkIds);
}

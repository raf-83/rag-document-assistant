package com.aidocumentvault.backend.ai.embedding.event;

import java.util.UUID;

public record DocumentChunksReadyForEmbeddingEvent(
    UUID documentId,
    UUID documentContentId,
    UUID userId,
    int chunkCount
) {
}

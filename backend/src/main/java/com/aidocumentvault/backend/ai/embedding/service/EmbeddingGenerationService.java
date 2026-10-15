package com.aidocumentvault.backend.ai.embedding.service;

import java.util.UUID;

public interface EmbeddingGenerationService {

    void generateEmbeddingsForDocumentContent(
        UUID documentId,
        UUID documentContentId,
        UUID userId
    );
}

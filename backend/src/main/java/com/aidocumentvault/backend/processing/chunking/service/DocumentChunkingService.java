package com.aidocumentvault.backend.processing.chunking.service;

import java.util.UUID;

public interface DocumentChunkingService {

    void chunkDocumentContent(UUID documentId, UUID documentContentId, UUID userId);
}

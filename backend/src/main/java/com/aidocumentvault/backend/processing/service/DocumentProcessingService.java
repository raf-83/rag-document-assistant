package com.aidocumentvault.backend.processing.service;

import java.util.UUID;

public interface DocumentProcessingService {

    void processDocument(UUID documentId, UUID userId);
}

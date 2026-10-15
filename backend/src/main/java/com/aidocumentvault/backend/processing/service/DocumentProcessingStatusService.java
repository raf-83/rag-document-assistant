package com.aidocumentvault.backend.processing.service;

import java.util.UUID;

public interface DocumentProcessingStatusService {

    void markAsTextProcessing(UUID documentId);

    void markAsContentReady(UUID documentId);

    void markAsChunking(UUID documentId);

    void markAsReady(UUID documentId);

    void markAsFailed(UUID documentId);
}

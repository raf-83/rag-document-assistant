package com.aidocumentvault.backend.processing.message;

import java.util.Objects;
import java.util.UUID;

public record DocumentProcessingRequested(
    String eventType,
    int schemaVersion,
    UUID jobId,
    UUID documentId,
    UUID correlationId
) {

    public static final String EVENT_TYPE = "document.processing.requested";
    public static final int SCHEMA_VERSION = 1;

    public DocumentProcessingRequested {
        // Obecnie obsługujemy jeden rodzaj wiadomości i wersję kontraktu.
        if (!EVENT_TYPE.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported eventType");
        }

        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schemaVersion");
        }

        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    public static DocumentProcessingRequested create(
        UUID jobId,
        UUID documentId,
        UUID correlationId
    ) {
        return new DocumentProcessingRequested(
            EVENT_TYPE,
            SCHEMA_VERSION,
            jobId,
            documentId,
            correlationId
        );
    }
}

package com.aidocumentvault.backend.ai.retrieval.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**Ten konkretny chunk został znaleziony przez wspólny silnik i otrzymał taki wynik RRF.*/
public record HybridRetrievedChunk(
    UUID documentId,
    String documentTitle,
    UUID documentTypeId,
    String documentTypeName,

    UUID chunkId,
    int chunkIndex,
    String text,
    int startCharIndex,
    int endCharIndex,

    double score,
    double semanticScore,
    double keywordScore,
    HybridMatchType matchType,

    Instant documentCreatedAt
) {

    public HybridRetrievedChunk {
        Objects.requireNonNull(
            documentId,
            "Document id must not be null"
        );
        Objects.requireNonNull(
            documentTitle,
            "Document title must not be null"
        );
        Objects.requireNonNull(
            documentTypeId,
            "Document type id must not be null"
        );
        Objects.requireNonNull(
            documentTypeName,
            "Document type name must not be null"
        );
        Objects.requireNonNull(
            chunkId,
            "Chunk id must not be null"
        );
        Objects.requireNonNull(
            text,
            "Chunk text must not be null"
        );
        Objects.requireNonNull(
            matchType,
            "Hybrid match type must not be null"
        );
        Objects.requireNonNull(
            documentCreatedAt,
            "Document created at must not be null"
        );

        if (chunkIndex < 0) {
            throw new IllegalArgumentException(
                "Chunk index must not be negative"
            );
        }

        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Chunk text must not be blank"
            );
        }

        if (startCharIndex < 0) {
            throw new IllegalArgumentException(
                "Start char index must not be negative"
            );
        }

        if (endCharIndex <= startCharIndex) {
            throw new IllegalArgumentException(
                "End char index must be greater than start char index"
            );
        }

        if (!Double.isFinite(score)
            || !Double.isFinite(semanticScore)
            || !Double.isFinite(keywordScore)) {

            throw new IllegalArgumentException(
                "Retrieved chunk scores must be finite"
            );
        }

        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(
                "Hybrid score must be between 0 and 1"
            );
        }
    }
}

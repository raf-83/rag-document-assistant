package com.aidocumentvault.backend.ai.retrieval.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KeywordRetrievedChunk(
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
    Instant documentCreatedAt
) {

    public KeywordRetrievedChunk {
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
            documentCreatedAt,
            "Document creation time must not be null"
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

        if (!Double.isFinite(score) || score < 0.0) {
            throw new IllegalArgumentException(
                "Keyword score must be finite and non-negative"
            );
        }
    }
}

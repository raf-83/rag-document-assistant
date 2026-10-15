package com.aidocumentvault.backend.ai.retrieval.model;

import java.util.Objects;
import java.util.UUID;

public record RetrievedDocumentChunk(
    UUID chunkId,
    int chunkIndex,
    String text,
    int startCharIndex,
    int endCharIndex,
    double score
) {
    public RetrievedDocumentChunk {
        Objects.requireNonNull(
            chunkId,
            "Chunk id must not be null"
        );

        Objects.requireNonNull(
            text,
            "Chunk text must not be null"
        );

        if (chunkIndex < 0) {
            throw new IllegalArgumentException(
                "Chunk index must not be negative"
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

        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException(
                "Score must be finite"
            );
        }
    }
}

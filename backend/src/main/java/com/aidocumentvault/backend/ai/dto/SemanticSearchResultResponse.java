package com.aidocumentvault.backend.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record SemanticSearchResultResponse(
    UUID documentId,
    String documentTitle,
    UUID documentTypeId,
    String documentTypeName,
    UUID chunkId,
    int chunkIndex,
    double score,
    String textPreview,
    Instant documentCreatedAt
) {
}

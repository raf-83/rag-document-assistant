package com.aidocumentvault.backend.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HybridSearchResultResponse(
    UUID documentId,
    String documentTitle,
    UUID documentTypeId,
    String documentTypeName,
    double score,
    double bestChunkScore,
    int matchedChunkCount,
    List<HybridSearchChunkResponse> chunks,
    Instant documentCreatedAt
) {

    public HybridSearchResultResponse {
        chunks = chunks == null
            ? List.of()
            : List.copyOf(chunks);
    }
}

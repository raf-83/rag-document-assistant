package com.aidocumentvault.backend.ai.dto;

import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;

import java.util.UUID;

public record HybridSearchChunkResponse(
    UUID chunkId,
    int chunkIndex,
    double score,
    double semanticScore,
    double keywordScore,
    HybridMatchType matchType,
    String textPreview
) {
}

package com.aidocumentvault.backend.ai.dto;

import java.util.List;

public record SemanticSearchResponse(
    int limit,
    int resultCount,
    List<SemanticSearchResultResponse> results
) {

    public SemanticSearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
        resultCount = results.size();
    }
}

package com.aidocumentvault.backend.ai.dto;

import java.util.List;

public record HybridSearchResponse(
    int limit,
    int resultCount,
    List<HybridSearchResultResponse> results
) {

    public HybridSearchResponse {
        results = results == null
            ? List.of()
            : List.copyOf(results);

        resultCount = results.size();
    }
}

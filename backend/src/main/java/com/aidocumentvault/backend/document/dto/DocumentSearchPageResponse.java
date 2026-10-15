package com.aidocumentvault.backend.document.dto;

import java.util.List;

public record DocumentSearchPageResponse(
        List<DocumentSearchResultResponse> results,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

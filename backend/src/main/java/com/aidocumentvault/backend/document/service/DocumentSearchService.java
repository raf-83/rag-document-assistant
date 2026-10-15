package com.aidocumentvault.backend.document.service;

import com.aidocumentvault.backend.document.dto.DocumentSearchPageResponse;

public interface DocumentSearchService {

    DocumentSearchPageResponse searchDocuments(
            String query,
            int page,
            int size
    );
}

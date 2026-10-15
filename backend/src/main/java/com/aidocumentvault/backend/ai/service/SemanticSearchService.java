package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.dto.SemanticSearchRequest;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResponse;

public interface SemanticSearchService {

    SemanticSearchResponse search(SemanticSearchRequest request);
}

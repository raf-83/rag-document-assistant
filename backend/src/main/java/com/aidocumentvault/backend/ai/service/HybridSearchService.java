package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.dto.HybridSearchRequest;
import com.aidocumentvault.backend.ai.dto.HybridSearchResponse;

public interface HybridSearchService {

    HybridSearchResponse search(HybridSearchRequest request);
}

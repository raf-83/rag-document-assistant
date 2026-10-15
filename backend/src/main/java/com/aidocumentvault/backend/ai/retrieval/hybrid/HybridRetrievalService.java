package com.aidocumentvault.backend.ai.retrieval.hybrid;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;

import java.util.List;

public interface HybridRetrievalService {

    List<HybridRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    );
}

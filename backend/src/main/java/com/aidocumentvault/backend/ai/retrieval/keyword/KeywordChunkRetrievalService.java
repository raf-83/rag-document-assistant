package com.aidocumentvault.backend.ai.retrieval.keyword;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.KeywordRetrievedChunk;

import java.util.List;

public interface KeywordChunkRetrievalService {

    List<KeywordRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    );
}

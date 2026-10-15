package com.aidocumentvault.backend.ai.retrieval.semantic;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;

import java.util.List;

public interface SemanticChunkRetrievalService {

    List<SemanticRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    );
}

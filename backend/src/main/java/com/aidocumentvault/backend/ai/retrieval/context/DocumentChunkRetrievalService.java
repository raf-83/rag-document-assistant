package com.aidocumentvault.backend.ai.retrieval.context;

import com.aidocumentvault.backend.ai.retrieval.model.RetrievedDocumentChunk;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRetrievalService {

    List<RetrievedDocumentChunk> retrieveRelevantChunks(
            UUID documentId,
            UUID ownerId,
            String question
    );
}

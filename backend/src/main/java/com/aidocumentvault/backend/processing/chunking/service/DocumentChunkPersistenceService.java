package com.aidocumentvault.backend.processing.chunking.service;

import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;

import java.util.List;

public interface DocumentChunkPersistenceService {

    int replaceChunks(DocumentContent documentContent, List<ChunkCandidate> chunkCandidates);
}

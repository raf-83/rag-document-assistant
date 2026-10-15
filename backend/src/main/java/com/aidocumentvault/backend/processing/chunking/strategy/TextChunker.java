package com.aidocumentvault.backend.processing.chunking.strategy;

import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;

import java.util.List;

public interface TextChunker {

    List<ChunkCandidate> chunk(String text);
}

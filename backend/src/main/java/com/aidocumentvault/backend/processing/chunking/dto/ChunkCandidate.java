package com.aidocumentvault.backend.processing.chunking.dto;

public record ChunkCandidate(
        int chunkIndex,
        String text,
        int startCharIndex,
        int endCharIndex,
        int characterCount,
        Integer estimatedTokenCount,
        int overlapPreviousChars,
        int overlapNextChars
) {
}

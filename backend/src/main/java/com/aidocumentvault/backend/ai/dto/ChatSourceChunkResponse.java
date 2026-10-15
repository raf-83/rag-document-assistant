package com.aidocumentvault.backend.ai.dto;

import java.util.UUID;

public record ChatSourceChunkResponse(
        UUID chunkId,
        int chunkIndex,
        int startCharIndex,
        int endCharIndex,
        Double score
) {
}

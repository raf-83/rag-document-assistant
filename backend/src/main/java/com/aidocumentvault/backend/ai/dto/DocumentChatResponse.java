package com.aidocumentvault.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentChatResponse(
        UUID documentId,
        String answer,
        List<ChatSourceChunkResponse> sources,
        boolean contextFound,
        OffsetDateTime createdAt
) {

    public static DocumentChatResponse noContext(UUID documentId, String answer) {
        return new DocumentChatResponse(
                documentId,
                answer,
                List.of(),
                false,
                OffsetDateTime.now()
        );
    }

    public static DocumentChatResponse withContext(
            UUID documentId,
            String answer,
            List<ChatSourceChunkResponse> sources
    ) {
        return new DocumentChatResponse(
                documentId,
                answer,
                sources,
                true,
                OffsetDateTime.now()
        );
    }
}

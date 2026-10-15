package com.aidocumentvault.backend.document.dto;

import com.aidocumentvault.backend.document.entity.DocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSearchResultResponse(
        UUID documentId,
        String title,
        String documentType,
        DocumentStatus status,
        String titleSnippet,
        String contentSnippet,
        boolean matchedInTitle,
        boolean matchedInContent,
        BigDecimal rank,
        Instant createdAt,
        Instant  updatedAt
) {
}

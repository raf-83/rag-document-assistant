package com.aidocumentvault.backend.document.dto;

import com.aidocumentvault.backend.document.entity.ExtractionMethod;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentContentResponse(
        UUID documentId,
        String sanitizedText,
        ExtractionMethod extractionMethod,
        Integer characterCount,
        Integer pageCount,
        OffsetDateTime extractedAt,
        OffsetDateTime sanitizedAt
) {
}

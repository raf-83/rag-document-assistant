package com.aidocumentvault.backend.processing.event;

import java.util.UUID;

public record DocumentUploadedEvent(
        UUID documentId,
        UUID userId
) {
}

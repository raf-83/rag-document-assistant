package com.aidocumentvault.backend.processing.chunking.event;

import java.util.UUID;

public record DocumentContentReadyEvent(
        UUID documentId,
        UUID documentContentId,
        UUID userId
) {
}

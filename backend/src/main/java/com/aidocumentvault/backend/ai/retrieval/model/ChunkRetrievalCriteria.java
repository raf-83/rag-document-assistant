package com.aidocumentvault.backend.ai.retrieval.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ChunkRetrievalCriteria(
    String query,
    UUID ownerId,
    UUID documentTypeId,
    OffsetDateTime createdFrom,
    OffsetDateTime createdTo,
    Set<UUID> scopeDocumentIds,
    int retrievedChunkLimit
) {

    public ChunkRetrievalCriteria {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                "Retrieval query must not be blank"
            );
        }

        if (ownerId == null) {
            throw new IllegalArgumentException(
                "Retrieval owner id must not be null"
            );
        }

        if (createdFrom != null
            && createdTo != null
            && createdFrom.isAfter(createdTo)) {

            throw new IllegalArgumentException(
                "createdFrom must be before or equal to createdTo"
            );
        }

        if (retrievedChunkLimit < 1) {
            throw new IllegalArgumentException(
                "Retrieved chunk limit must be at least 1"
            );
        }

        if (scopeDocumentIds != null
            && scopeDocumentIds
            .stream()
            .anyMatch(Objects::isNull)) {

            throw new IllegalArgumentException(
                "Scope document ids must not contain null"
            );
        }

        query = query.trim();

        scopeDocumentIds =
            scopeDocumentIds == null
                ? Set.of()
                : Set.copyOf(scopeDocumentIds);
    }

    public boolean hasScopeDocumentIds() {
        return !scopeDocumentIds.isEmpty();
    }
}

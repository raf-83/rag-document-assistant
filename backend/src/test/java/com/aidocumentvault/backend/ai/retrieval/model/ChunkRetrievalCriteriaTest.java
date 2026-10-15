package com.aidocumentvault.backend.ai.retrieval.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChunkRetrievalCriteriaTest {

    @Test
    void shouldNormalizeQueryAndDefensivelyCopyScopeDocumentIds() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        Set<UUID> mutableScopeDocumentIds =
            new LinkedHashSet<>();

        mutableScopeDocumentIds.add(documentId);

        // when
        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                "  prawo do zasiłku  ",
                ownerId,
                null,
                null,
                null,
                mutableScopeDocumentIds,
                15
            );

        mutableScopeDocumentIds.clear();

        // then
        assertThat(criteria.query())
            .isEqualTo("prawo do zasiłku");

        assertThat(criteria.ownerId())
            .isEqualTo(ownerId);

        assertThat(criteria.scopeDocumentIds())
            .containsExactly(documentId);

        assertThat(criteria.hasScopeDocumentIds())
            .isTrue();

        assertThat(criteria.retrievedChunkLimit())
            .isEqualTo(15);
    }

    @Test
    void shouldReplaceNullScopeWithEmptySet() {
        // when
        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                15
            );

        // then
        assertThat(criteria.scopeDocumentIds()).isEmpty();
        assertThat(criteria.hasScopeDocumentIds()).isFalse();
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThatThrownBy(() ->
            new ChunkRetrievalCriteria(
                " ",
                UUID.randomUUID(),
                null,
                null,
                null,
                Set.of(),
                15
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Retrieval query must not be blank");
    }

    @Test
    void shouldRejectNullOwnerId() {
        assertThatThrownBy(() ->
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                null,
                null,
                null,
                null,
                Set.of(),
                15
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Retrieval owner id must not be null");
    }

    @Test
    void shouldRejectInvalidDateRange() {
        // given
        OffsetDateTime createdFrom =
            OffsetDateTime.parse("2026-07-20T10:00:00Z");

        OffsetDateTime createdTo =
            OffsetDateTime.parse("2026-07-10T10:00:00Z");

        // when / then
        assertThatThrownBy(() ->
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                UUID.randomUUID(),
                null,
                createdFrom,
                createdTo,
                Set.of(),
                15
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "createdFrom must be before or equal to createdTo"
            );
    }

    @Test
    void shouldRejectNonPositiveRetrievedChunkLimit() {
        assertThatThrownBy(() ->
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                UUID.randomUUID(),
                null,
                null,
                null,
                Set.of(),
                0
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Retrieved chunk limit must be at least 1"
            );
    }

    @Test
    void shouldRejectNullScopeDocumentId() {
        // given
        Set<UUID> scopeDocumentIds =
            new LinkedHashSet<>();

        scopeDocumentIds.add(UUID.randomUUID());
        scopeDocumentIds.add(null);

        // when / then
        assertThatThrownBy(() ->
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                UUID.randomUUID(),
                null,
                null,
                null,
                scopeDocumentIds,
                15
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Scope document ids must not contain null"
            );
    }
}

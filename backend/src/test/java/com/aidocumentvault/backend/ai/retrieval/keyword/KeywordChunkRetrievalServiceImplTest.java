package com.aidocumentvault.backend.ai.retrieval.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.KeywordRetrievedChunk;
import com.aidocumentvault.backend.ai.search.persistence.KeywordSearchRepository;
import com.aidocumentvault.backend.ai.search.persistence.KeywordSearchResultRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeywordChunkRetrievalServiceImplTest {

    @Mock
    private KeywordSearchRepository keywordSearchRepository;

    @Mock
    private KeywordSearchResultRow resultRow;

    private KeywordChunkRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeywordChunkRetrievalServiceImpl(
            keywordSearchRepository
        );
    }

    @Test
    void shouldForwardCriteriaAndReturnFullChunk() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID documentTypeId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        OffsetDateTime createdFrom =
            OffsetDateTime.parse(
                "2026-07-01T10:00:00Z"
            );

        OffsetDateTime createdTo =
            OffsetDateTime.parse(
                "2026-07-20T10:00:00Z"
            );

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                ownerId,
                documentTypeId,
                createdFrom,
                createdTo,
                Set.of(documentId),
                15
            );

        String fullChunkText =
            "a".repeat(800);

        Instant documentCreatedAt =
            Instant.parse(
                "2026-07-05T12:00:00Z"
            );

        when(
            keywordSearchRepository.findKeywordChunks(
                "prawo do zasiłku",
                ownerId,
                documentTypeId,
                createdFrom,
                createdTo,
                false,
                Set.of(documentId),
                15
            )
        ).thenReturn(List.of(resultRow));

        when(resultRow.getDocumentId())
            .thenReturn(documentId);

        when(resultRow.getDocumentTitle())
            .thenReturn("Decyzja o zasiłku");

        when(resultRow.getDocumentTypeId())
            .thenReturn(documentTypeId);

        when(resultRow.getDocumentTypeName())
            .thenReturn("Dokument urzędowy");

        when(resultRow.getChunkId())
            .thenReturn(chunkId);

        when(resultRow.getChunkIndex())
            .thenReturn(2);

        when(resultRow.getText())
            .thenReturn(fullChunkText);

        when(resultRow.getStartCharIndex())
            .thenReturn(1200);

        when(resultRow.getEndCharIndex())
            .thenReturn(2000);

        when(resultRow.getScore())
            .thenReturn(0.18);

        when(resultRow.getDocumentCreatedAt())
            .thenReturn(documentCreatedAt);

        // when
        List<KeywordRetrievedChunk> results =
            service.retrieve(criteria);

        // then
        assertThat(results)
            .singleElement()
            .satisfies(result -> {
                assertThat(result.documentId())
                    .isEqualTo(documentId);

                assertThat(result.documentTitle())
                    .isEqualTo("Decyzja o zasiłku");

                assertThat(result.documentTypeId())
                    .isEqualTo(documentTypeId);

                assertThat(result.documentTypeName())
                    .isEqualTo("Dokument urzędowy");

                assertThat(result.chunkId())
                    .isEqualTo(chunkId);

                assertThat(result.chunkIndex())
                    .isEqualTo(2);

                assertThat(result.text())
                    .isEqualTo(fullChunkText)
                    .hasSize(800);

                assertThat(result.startCharIndex())
                    .isEqualTo(1200);

                assertThat(result.endCharIndex())
                    .isEqualTo(2000);

                assertThat(result.score())
                    .isEqualTo(0.18);

                assertThat(result.documentCreatedAt())
                    .isEqualTo(documentCreatedAt);
            });

        verify(keywordSearchRepository)
            .findKeywordChunks(
                "prawo do zasiłku",
                ownerId,
                documentTypeId,
                createdFrom,
                createdTo,
                false,
                Set.of(documentId),
                15
            );
    }

    @Test
    void shouldUseDummyDocumentIdForEmptyScope() {
        // given
        UUID ownerId = UUID.randomUUID();

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                "prawo do zasiłku",
                ownerId,
                null,
                null,
                null,
                Set.of(),
                15
            );

        when(
            keywordSearchRepository.findKeywordChunks(
                eq("prawo do zasiłku"),
                eq(ownerId),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                org.mockito.ArgumentMatchers
                    .anyCollection(),
                eq(15)
            )
        ).thenReturn(List.of());

        // when
        List<KeywordRetrievedChunk> results =
            service.retrieve(criteria);

        // then
        assertThat(results).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>>
            scopeCaptor =
                ArgumentCaptor.forClass(
                    Collection.class
                );

        verify(keywordSearchRepository)
            .findKeywordChunks(
                eq("prawo do zasiłku"),
                eq(ownerId),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                scopeCaptor.capture(),
                eq(15)
            );

        assertThat(scopeCaptor.getValue())
            .containsExactly(
                UUID.fromString(
                    "00000000-0000-0000-0000-000000000000"
                )
            );
    }
}

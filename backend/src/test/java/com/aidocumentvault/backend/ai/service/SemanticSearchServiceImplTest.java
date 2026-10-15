package com.aidocumentvault.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.dto.SemanticSearchRequest;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResponse;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.semantic.SemanticChunkRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class SemanticSearchServiceImplTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private SemanticChunkRetrievalService
        semanticChunkRetrievalService;

    private SemanticSearchServiceImpl semanticSearchService;

    @BeforeEach
    void setUp() {
        semanticSearchService =
            new SemanticSearchServiceImpl(
                currentUserProvider,
                semanticChunkRetrievalService
            );
    }

    @Test
    void shouldCreateCriteriaAndMapRetrievedChunk() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID documentTypeId = UUID.randomUUID();
        UUID scopedDocumentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        OffsetDateTime createdFrom =
            OffsetDateTime.parse(
                "2026-07-01T10:00:00Z"
            );

        OffsetDateTime createdTo =
            OffsetDateTime.parse(
                "2026-07-07T10:00:00Z"
            );

        Instant documentCreatedAt =
            Instant.parse(
                "2026-07-02T12:00:00Z"
            );

        SemanticSearchRequest request =
            new SemanticSearchRequest(
                "  ile wynosi zasiłek?  ",
                5,
                documentTypeId,
                createdFrom,
                createdTo,
                Set.of(scopedDocumentId)
            );

        String fullText =
            "Fragment dokumentu zawierający informację "
                + "o wysokości zasiłku.";

        SemanticRetrievedChunk retrievedChunk =
            new SemanticRetrievedChunk(
                documentId,
                "Decyzja",
                documentTypeId,
                "Dokument urzędowy",
                chunkId,
                3,
                fullText,
                100,
                170,
                0.91,
                documentCreatedAt
            );

        when(currentUserProvider.getCurrentUserId())
            .thenReturn(ownerId);

        when(
            semanticChunkRetrievalService.retrieve(
                org.mockito.ArgumentMatchers.any(
                    ChunkRetrievalCriteria.class
                )
            )
        ).thenReturn(List.of(retrievedChunk));

        // when
        SemanticSearchResponse response =
            semanticSearchService.search(request);

        // then
        assertThat(response.limit()).isEqualTo(5);
        assertThat(response.resultCount()).isEqualTo(1);

        assertThat(response.results())
            .singleElement()
            .satisfies(result -> {
                assertThat(result.documentId())
                    .isEqualTo(documentId);

                assertThat(result.documentTitle())
                    .isEqualTo("Decyzja");

                assertThat(result.documentTypeId())
                    .isEqualTo(documentTypeId);

                assertThat(result.documentTypeName())
                    .isEqualTo("Dokument urzędowy");

                assertThat(result.chunkId())
                    .isEqualTo(chunkId);

                assertThat(result.chunkIndex())
                    .isEqualTo(3);

                assertThat(result.score())
                    .isEqualTo(0.91);

                assertThat(result.textPreview())
                    .isEqualTo(fullText);

                assertThat(result.documentCreatedAt())
                    .isEqualTo(documentCreatedAt);
            });

        ArgumentCaptor<ChunkRetrievalCriteria>
            criteriaCaptor =
            ArgumentCaptor.forClass(
                ChunkRetrievalCriteria.class
            );

        verify(semanticChunkRetrievalService)
            .retrieve(criteriaCaptor.capture());

        ChunkRetrievalCriteria criteria =
            criteriaCaptor.getValue();

        assertThat(criteria.query())
            .isEqualTo("ile wynosi zasiłek?");

        assertThat(criteria.ownerId())
            .isEqualTo(ownerId);

        assertThat(criteria.documentTypeId())
            .isEqualTo(documentTypeId);

        assertThat(criteria.createdFrom())
            .isEqualTo(createdFrom);

        assertThat(criteria.createdTo())
            .isEqualTo(createdTo);

        assertThat(criteria.scopeDocumentIds())
            .containsExactly(scopedDocumentId);

        assertThat(criteria.retrievedChunkLimit())
            .isEqualTo(5);
    }

    @Test
    void shouldUseDefaultLimitAndEmptyScope() {
        // given
        UUID ownerId = UUID.randomUUID();

        SemanticSearchRequest request =
            new SemanticSearchRequest(
                "ile wynosi zasiłek?",
                null,
                null,
                null,
                null,
                null
            );

        when(currentUserProvider.getCurrentUserId())
            .thenReturn(ownerId);

        when(
            semanticChunkRetrievalService.retrieve(
                org.mockito.ArgumentMatchers.any(
                    ChunkRetrievalCriteria.class
                )
            )
        ).thenReturn(List.of());

        // when
        SemanticSearchResponse response =
            semanticSearchService.search(request);

        // then
        assertThat(response.limit())
            .isEqualTo(
                SemanticSearchRequest.DEFAULT_LIMIT
            );

        assertThat(response.resultCount()).isZero();
        assertThat(response.results()).isEmpty();

        ArgumentCaptor<ChunkRetrievalCriteria>
            criteriaCaptor =
            ArgumentCaptor.forClass(
                ChunkRetrievalCriteria.class
            );

        verify(semanticChunkRetrievalService)
            .retrieve(criteriaCaptor.capture());

        ChunkRetrievalCriteria criteria =
            criteriaCaptor.getValue();

        assertThat(criteria.ownerId())
            .isEqualTo(ownerId);

        assertThat(criteria.scopeDocumentIds())
            .isEmpty();

        assertThat(criteria.retrievedChunkLimit())
            .isEqualTo(
                SemanticSearchRequest.DEFAULT_LIMIT
            );
    }

    @Test
    void shouldLimitPublicTextPreviewToFiveHundredCharacters() {
        // given
        UUID ownerId = UUID.randomUUID();

        SemanticSearchRequest request =
            new SemanticSearchRequest(
                "zasiłek",
                5,
                null,
                null,
                null,
                null
            );

        String fullText = "a".repeat(800);

        SemanticRetrievedChunk retrievedChunk =
            new SemanticRetrievedChunk(
                UUID.randomUUID(),
                "Decyzja",
                UUID.randomUUID(),
                "Dokument urzędowy",
                UUID.randomUUID(),
                0,
                fullText,
                0,
                800,
                0.8,
                Instant.parse(
                    "2026-07-02T12:00:00Z"
                )
            );

        when(currentUserProvider.getCurrentUserId())
            .thenReturn(ownerId);

        when(
            semanticChunkRetrievalService.retrieve(
                org.mockito.ArgumentMatchers.any(
                    ChunkRetrievalCriteria.class
                )
            )
        ).thenReturn(List.of(retrievedChunk));

        // when
        SemanticSearchResponse response =
            semanticSearchService.search(request);

        // then
        assertThat(
            response.results()
                .getFirst()
                .textPreview()
        )
            .hasSize(500)
            .isEqualTo("a".repeat(500));
    }
}

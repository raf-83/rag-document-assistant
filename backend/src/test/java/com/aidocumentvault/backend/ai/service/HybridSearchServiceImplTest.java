package com.aidocumentvault.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;
import com.aidocumentvault.backend.ai.dto.HybridSearchChunkResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchRequest;
import com.aidocumentvault.backend.ai.dto.HybridSearchResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchResultResponse;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.hybrid.HybridRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
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
class HybridSearchServiceImplTest {

    private static final UUID OWNER_ID = UUID.randomUUID();

    private static final UUID DOCUMENT_TYPE_ID =
        UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final Instant DOCUMENT_CREATED_AT =
        Instant.parse("2026-07-01T10:00:00Z");

    private static final AiHybridSearchProperties PROPERTIES =
        new AiHybridSearchProperties(
            60,
            0.60,
            0.40,
            3,
            150,
            3,
            0.15
        );

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private HybridRetrievalService hybridRetrievalService;

    private HybridSearchServiceImpl hybridSearchService;

    @BeforeEach
    void setUp() {
        hybridSearchService = new HybridSearchServiceImpl(
            currentUserProvider,
            hybridRetrievalService,
            PROPERTIES
        );

        when(currentUserProvider.getCurrentUserId())
            .thenReturn(OWNER_ID);
    }

    @Test
    void shouldGroupRankAndLimitRetrievedChunksByDocument() {
        // given
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();

        UUID bestChunkId = UUID.randomUUID();
        UUID secondChunkId = UUID.randomUUID();
        UUID thirdChunkId = UUID.randomUUID();
        UUID hiddenChunkId = UUID.randomUUID();
        UUID otherDocumentChunkId = UUID.randomUUID();

        HybridSearchRequest request = new HybridSearchRequest(
            "prawo do zasilku",
            3,
            null,
            null,
            null,
            Set.of()
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(
            List.of(
                retrievedChunk(
                    firstDocumentId,
                    hiddenChunkId,
                    3,
                    0.10,
                    "Hidden chunk"
                ),
                retrievedChunk(
                    secondDocumentId,
                    otherDocumentChunkId,
                    0,
                    0.905,
                    "Other document chunk"
                ),
                retrievedChunk(
                    firstDocumentId,
                    bestChunkId,
                    0,
                    0.90,
                    "a".repeat(800)
                ),
                retrievedChunk(
                    firstDocumentId,
                    thirdChunkId,
                    2,
                    0.70,
                    "Third chunk"
                ),
                retrievedChunk(
                    firstDocumentId,
                    secondChunkId,
                    1,
                    0.80,
                    "Second chunk"
                )
            )
        );

        double expectedFirstDocumentScore =
            documentScore(
                0.90,
                0.80,
                0.70,
                0.10
            );

        // when
        HybridSearchResponse response =
            hybridSearchService.search(request);

        // then
        assertThat(response.limit()).isEqualTo(3);
        assertThat(response.resultCount()).isEqualTo(2);

        HybridSearchResultResponse firstResult =
            response.results().getFirst();

        assertThat(firstResult.documentId())
            .isEqualTo(firstDocumentId);

        assertThat(firstResult.bestChunkScore())
            .isCloseTo(0.90, within(0.000001));

        assertThat(firstResult.score())
            .isCloseTo(
                expectedFirstDocumentScore,
                within(0.000001)
            );

        assertThat(firstResult.matchedChunkCount())
            .isEqualTo(4);

        assertThat(firstResult.chunks())
            .extracting(HybridSearchChunkResponse::chunkId)
            .containsExactly(
                bestChunkId,
                secondChunkId,
                thirdChunkId
            );

        HybridSearchChunkResponse bestChunk =
            firstResult.chunks().getFirst();

        assertThat(bestChunk.textPreview())
            .hasSize(500);

        assertThat(bestChunk.matchType())
            .isEqualTo(HybridMatchType.HYBRID);

        HybridSearchResultResponse secondResult =
            response.results().get(1);

        assertThat(secondResult.documentId())
            .isEqualTo(secondDocumentId);

        assertThat(secondResult.score())
            .isCloseTo(0.905, within(0.000001));
    }

    @Test
    void shouldPassNormalizedQueryFiltersAndCandidateLimit() {
        // given
        UUID documentTypeId = UUID.randomUUID();
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();

        OffsetDateTime createdFrom =
            OffsetDateTime.parse("2026-01-01T00:00:00Z");

        OffsetDateTime createdTo =
            OffsetDateTime.parse("2026-12-31T23:59:59Z");

        Set<UUID> scopeDocumentIds =
            Set.of(firstDocumentId, secondDocumentId);

        HybridSearchRequest request = new HybridSearchRequest(
            "   decyzja o swiadczeniu   ",
            5,
            documentTypeId,
            createdFrom,
            createdTo,
            scopeDocumentIds
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(List.of());

        ArgumentCaptor<ChunkRetrievalCriteria> criteriaCaptor =
            ArgumentCaptor.forClass(ChunkRetrievalCriteria.class);

        // when
        HybridSearchResponse response =
            hybridSearchService.search(request);

        // then
        assertThat(response.limit()).isEqualTo(5);
        assertThat(response.resultCount()).isZero();
        assertThat(response.results()).isEmpty();

        verify(hybridRetrievalService)
            .retrieve(criteriaCaptor.capture());

        ChunkRetrievalCriteria criteria =
            criteriaCaptor.getValue();

        assertThat(criteria.query())
            .isEqualTo("decyzja o swiadczeniu");

        assertThat(criteria.ownerId())
            .isEqualTo(OWNER_ID);

        assertThat(criteria.documentTypeId())
            .isEqualTo(documentTypeId);

        assertThat(criteria.createdFrom())
            .isEqualTo(createdFrom);

        assertThat(criteria.createdTo())
            .isEqualTo(createdTo);

        assertThat(criteria.scopeDocumentIds())
            .containsExactlyInAnyOrderElementsOf(scopeDocumentIds);

        assertThat(criteria.retrievedChunkLimit())
            .isEqualTo(PROPERTIES.candidateLimitFor(5));
    }

    @Test
    void shouldPassEmptyScopeToRetrieval() {
        // given
        HybridSearchRequest request = new HybridSearchRequest(
            "zasilek",
            5,
            null,
            null,
            null,
            null
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(List.of());

        ArgumentCaptor<ChunkRetrievalCriteria> criteriaCaptor =
            ArgumentCaptor.forClass(ChunkRetrievalCriteria.class);

        // when
        hybridSearchService.search(request);

        // then
        verify(hybridRetrievalService)
            .retrieve(criteriaCaptor.capture());

        ChunkRetrievalCriteria criteria =
            criteriaCaptor.getValue();

        assertThat(criteria.scopeDocumentIds()).isEmpty();
        assertThat(criteria.hasScopeDocumentIds()).isFalse();
    }

    @Test
    void shouldRejectNullRetrievalResults() {
        // given
        HybridSearchRequest request = new HybridSearchRequest(
            "zasilek",
            5,
            null,
            null,
            null,
            Set.of()
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> hybridSearchService.search(request))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Hybrid retrieval results must not be null");
    }

    @Test
    void shouldRejectNonPositiveBestChunkScore() {
        // given
        HybridSearchRequest request = new HybridSearchRequest(
            "zasilek",
            5,
            null,
            null,
            null,
            Set.of()
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(
            List.of(
                retrievedChunk(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    0,
                    0.0,
                    "Zero score chunk"
                )
            )
        );

        // when / then
        assertThatThrownBy(() -> hybridSearchService.search(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Best chunk score must be finite and greater than 0"
            );
    }

    @Test
    void shouldRejectInconsistentMetadataForSameDocument() {
        // given
        UUID documentId = UUID.randomUUID();

        HybridSearchRequest request = new HybridSearchRequest(
            "zasilek",
            5,
            null,
            null,
            null,
            Set.of()
        );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(
            List.of(
                retrievedChunk(
                    documentId,
                    "First title",
                    UUID.randomUUID(),
                    0,
                    0.90,
                    "First chunk"
                ),
                retrievedChunk(
                    documentId,
                    "Second title",
                    UUID.randomUUID(),
                    1,
                    0.80,
                    "Second chunk"
                )
            )
        );

        // when / then
        assertThatThrownBy(() -> hybridSearchService.search(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Hybrid search returned inconsistent "
                    + "metadata for the same document"
            );
    }

    private HybridRetrievedChunk retrievedChunk(
        UUID documentId,
        UUID chunkId,
        int chunkIndex,
        double score,
        String text
    ) {
        return retrievedChunk(
            documentId,
            "Benefit decision",
            chunkId,
            chunkIndex,
            score,
            text
        );
    }

    private HybridRetrievedChunk retrievedChunk(
        UUID documentId,
        String documentTitle,
        UUID chunkId,
        int chunkIndex,
        double score,
        String text
    ) {
        return new HybridRetrievedChunk(
            documentId,
            documentTitle,
            DOCUMENT_TYPE_ID,
            "Official document",
            chunkId,
            chunkIndex,
            text,
            chunkIndex * 100,
            chunkIndex * 100 + text.length(),
            score,
            score,
            0.0,
            HybridMatchType.HYBRID,
            DOCUMENT_CREATED_AT
        );
    }

    private double documentScore(
        double bestChunkScore,
        double... otherChunkScores
    ) {
        double relativeEvidence = 0.0;

        for (double otherChunkScore : otherChunkScores) {
            relativeEvidence += Math.min(
                otherChunkScore / bestChunkScore,
                1.0
            );
        }

        double evidenceFactor =
            relativeEvidence / (1.0 + relativeEvidence);

        return bestChunkScore
            + (1.0 - bestChunkScore)
                * PROPERTIES.maxDocumentBonusShare()
                * evidenceFactor;
    }
}

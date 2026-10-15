package com.aidocumentvault.backend.ai.retrieval.hybrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.aidocumentvault.backend.ai.retrieval.keyword.KeywordChunkRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.model.KeywordRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.semantic.SemanticChunkRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceImplTest {

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
    private SemanticChunkRetrievalService
        semanticChunkRetrievalService;

    @Mock
    private KeywordChunkRetrievalService
        keywordChunkRetrievalService;

    private HybridRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HybridRetrievalServiceImpl(
            semanticChunkRetrievalService,
            keywordChunkRetrievalService,
            PROPERTIES
        );
    }

    @Test
    void shouldMergeRankAndClassifyRetrievedChunks() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID documentTypeId = UUID.randomUUID();

        UUID hybridChunkId = UUID.randomUUID();
        UUID semanticOnlyChunkId = UUID.randomUUID();
        UUID keywordOnlyChunkId = UUID.randomUUID();

        Instant documentCreatedAt =
            Instant.parse("2026-07-01T10:00:00Z");

        String hybridFullText = "a".repeat(800);

        ChunkRetrievalCriteria criteria =
            criteria(documentId);

        List<SemanticRetrievedChunk> semanticResults =
            List.of(
                semanticChunk(
                    documentId,
                    documentTypeId,
                    hybridChunkId,
                    0,
                    hybridFullText,
                    0,
                    800,
                    0.91,
                    documentCreatedAt
                ),
                semanticChunk(
                    documentId,
                    documentTypeId,
                    semanticOnlyChunkId,
                    1,
                    "Semantic only text",
                    700,
                    718,
                    0.82,
                    documentCreatedAt
                )
            );

        /*
         * Keyword ranking:
         * 1. keyword-only chunk
         * 2. hybrid chunk
         */
        List<KeywordRetrievedChunk> keywordResults =
            List.of(
                keywordChunk(
                    documentId,
                    documentTypeId,
                    keywordOnlyChunkId,
                    2,
                    "Keyword only text",
                    1400,
                    1417,
                    0.73,
                    documentCreatedAt
                ),
                keywordChunk(
                    documentId,
                    documentTypeId,
                    hybridChunkId,
                    0,
                    hybridFullText,
                    0,
                    800,
                    0.61,
                    documentCreatedAt
                )
            );

        when(
            semanticChunkRetrievalService.retrieve(criteria)
        ).thenReturn(semanticResults);

        when(
            keywordChunkRetrievalService.retrieve(criteria)
        ).thenReturn(keywordResults);

        // when
        List<HybridRetrievedChunk> results =
            service.retrieve(criteria);

        // then
        assertThat(results).hasSize(3);

        assertThat(results)
            .extracting(HybridRetrievedChunk::chunkId)
            .containsExactly(
                hybridChunkId,
                semanticOnlyChunkId,
                keywordOnlyChunkId
            );

        double maximumPossibleRrfScore =
            1.0 / 61.0;

        double expectedHybridScore = (
            0.60 / 61.0
                + 0.40 / 62.0
        ) / maximumPossibleRrfScore;

        double expectedSemanticOnlyScore =
            (0.60 / 62.0)
                / maximumPossibleRrfScore;

        double expectedKeywordOnlyScore =
            (0.40 / 61.0)
                / maximumPossibleRrfScore;

        HybridRetrievedChunk hybridChunk =
            results.getFirst();

        assertThat(hybridChunk.matchType())
            .isEqualTo(HybridMatchType.HYBRID);

        assertThat(hybridChunk.semanticScore())
            .isEqualTo(0.91);

        assertThat(hybridChunk.keywordScore())
            .isEqualTo(0.61);

        assertThat(hybridChunk.score())
            .isCloseTo(
                expectedHybridScore,
                within(0.000001)
            );

        assertThat(hybridChunk.text())
            .isEqualTo(hybridFullText)
            .hasSize(800);

        assertThat(hybridChunk.startCharIndex())
            .isZero();

        assertThat(hybridChunk.endCharIndex())
            .isEqualTo(800);

        HybridRetrievedChunk semanticOnlyChunk =
            results.get(1);

        assertThat(semanticOnlyChunk.matchType())
            .isEqualTo(
                HybridMatchType.SEMANTIC_ONLY
            );

        assertThat(semanticOnlyChunk.semanticScore())
            .isEqualTo(0.82);

        assertThat(semanticOnlyChunk.keywordScore())
            .isZero();

        assertThat(semanticOnlyChunk.score())
            .isCloseTo(
                expectedSemanticOnlyScore,
                within(0.000001)
            );

        HybridRetrievedChunk keywordOnlyChunk =
            results.get(2);

        assertThat(keywordOnlyChunk.matchType())
            .isEqualTo(
                HybridMatchType.KEYWORD_ONLY
            );

        assertThat(keywordOnlyChunk.semanticScore())
            .isZero();

        assertThat(keywordOnlyChunk.keywordScore())
            .isEqualTo(0.73);

        assertThat(keywordOnlyChunk.score())
            .isCloseTo(
                expectedKeywordOnlyScore,
                within(0.000001)
            );

        verify(semanticChunkRetrievalService)
            .retrieve(criteria);

        verify(keywordChunkRetrievalService)
            .retrieve(criteria);
    }

    @Test
    void shouldReturnEmptyListWhenBothBranchesAreEmpty() {
        // given
        ChunkRetrievalCriteria criteria =
            criteria(UUID.randomUUID());

        when(
            semanticChunkRetrievalService.retrieve(criteria)
        ).thenReturn(List.of());

        when(
            keywordChunkRetrievalService.retrieve(criteria)
        ).thenReturn(List.of());

        // when
        List<HybridRetrievedChunk> results =
            service.retrieve(criteria);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    void shouldRejectInconsistentMetadataForSameChunk() {
        // given
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID documentTypeId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        Instant documentCreatedAt =
            Instant.parse("2026-07-01T10:00:00Z");

        ChunkRetrievalCriteria criteria =
            criteria(firstDocumentId);

        SemanticRetrievedChunk semanticResult =
            semanticChunk(
                firstDocumentId,
                documentTypeId,
                chunkId,
                0,
                "Ten sam tekst",
                0,
                13,
                0.91,
                documentCreatedAt
            );

        KeywordRetrievedChunk keywordResult =
            keywordChunk(
                secondDocumentId,
                documentTypeId,
                chunkId,
                0,
                "Ten sam tekst",
                0,
                13,
                0.61,
                documentCreatedAt
            );

        when(
            semanticChunkRetrievalService.retrieve(criteria)
        ).thenReturn(List.of(semanticResult));

        when(
            keywordChunkRetrievalService.retrieve(criteria)
        ).thenReturn(List.of(keywordResult));

        // when / then
        assertThatThrownBy(() -> service.retrieve(criteria))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Hybrid retrieval returned inconsistent "
                    + "metadata for the same chunk"
            );
    }

    private ChunkRetrievalCriteria criteria(
        UUID documentId
    ) {
        return new ChunkRetrievalCriteria(
            "prawo do zasiłku",
            UUID.randomUUID(),
            null,
            null,
            null,
            Set.of(documentId),
            15
        );
    }

    private SemanticRetrievedChunk semanticChunk(
        UUID documentId,
        UUID documentTypeId,
        UUID chunkId,
        int chunkIndex,
        String text,
        int startCharIndex,
        int endCharIndex,
        double score,
        Instant documentCreatedAt
    ) {
        return new SemanticRetrievedChunk(
            documentId,
            "Decyzja o zasiłku",
            documentTypeId,
            "Dokument urzędowy",
            chunkId,
            chunkIndex,
            text,
            startCharIndex,
            endCharIndex,
            score,
            documentCreatedAt
        );
    }

    private KeywordRetrievedChunk keywordChunk(
        UUID documentId,
        UUID documentTypeId,
        UUID chunkId,
        int chunkIndex,
        String text,
        int startCharIndex,
        int endCharIndex,
        double score,
        Instant documentCreatedAt
    ) {
        return new KeywordRetrievedChunk(
            documentId,
            "Decyzja o zasiłku",
            documentTypeId,
            "Dokument urzędowy",
            chunkId,
            chunkIndex,
            text,
            startCharIndex,
            endCharIndex,
            score,
            documentCreatedAt
        );
    }
}

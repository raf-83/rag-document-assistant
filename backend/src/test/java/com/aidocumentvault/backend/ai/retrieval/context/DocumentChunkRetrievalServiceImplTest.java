package com.aidocumentvault.backend.ai.retrieval.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aidocumentvault.backend.ai.config.AiChatProperties;
import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aidocumentvault.backend.ai.retrieval.hybrid.HybridRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.model.RetrievedDocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentChunkRetrievalServiceImplTest {

    private static final UUID DOCUMENT_TYPE_ID =
        UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
        );

    private static final Instant DOCUMENT_CREATED_AT =
        Instant.parse("2026-07-01T10:00:00Z");

    private static final AiHybridSearchProperties
        HYBRID_PROPERTIES =
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
    private HybridRetrievalService hybridRetrievalService;

    private DocumentChunkRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentChunkRetrievalServiceImpl(
            hybridRetrievalService,
            new AiChatProperties(5, 8000),
            HYBRID_PROPERTIES
        );
    }

    @Test
    void shouldCreateDocumentScopedCriteriaAndMapHybridChunk() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        String fullChunkText =
            "To jest pełny fragment dokumentu "
                + "opisujący prawo do zasiłku.";

        HybridRetrievedChunk hybridChunk =
            hybridChunk(
                documentId,
                chunkId,
                2,
                fullChunkText,
                100,
                100 + fullChunkText.length(),
                0.97
            );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(List.of(hybridChunk));

        // when
        List<RetrievedDocumentChunk> results =
            service.retrieveRelevantChunks(
                documentId,
                ownerId,
                "  jakie mam prawo do świadczenia?  "
            );

        // then
        assertThat(results)
            .singleElement()
            .satisfies(result -> {
                assertThat(result.chunkId())
                    .isEqualTo(chunkId);

                assertThat(result.chunkIndex())
                    .isEqualTo(2);

                assertThat(result.text())
                    .isEqualTo(fullChunkText);

                assertThat(result.startCharIndex())
                    .isEqualTo(100);

                assertThat(result.endCharIndex())
                    .isEqualTo(
                        100 + fullChunkText.length()
                    );

                assertThat(result.score())
                    .isEqualTo(0.97);
            });

        ArgumentCaptor<ChunkRetrievalCriteria>
            criteriaCaptor =
            ArgumentCaptor.forClass(
                ChunkRetrievalCriteria.class
            );

        verify(hybridRetrievalService)
            .retrieve(criteriaCaptor.capture());

        ChunkRetrievalCriteria criteria =
            criteriaCaptor.getValue();

        assertThat(criteria.query())
            .isEqualTo(
                "jakie mam prawo do świadczenia?"
            );

        assertThat(criteria.ownerId())
            .isEqualTo(ownerId);

        assertThat(criteria.documentTypeId())
            .isNull();

        assertThat(criteria.createdFrom())
            .isNull();

        assertThat(criteria.createdTo())
            .isNull();

        assertThat(criteria.scopeDocumentIds())
            .containsExactly(documentId);

        /*
         * 5 finalnych chunków
         * × candidateMultiplier 3
         * = 15 kandydatów.
         */
        assertThat(criteria.retrievedChunkLimit())
            .isEqualTo(15);
    }

    @Test
    void shouldLimitFinalNumberOfChunks() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        DocumentChunkRetrievalServiceImpl limitedService =
            new DocumentChunkRetrievalServiceImpl(
                hybridRetrievalService,
                new AiChatProperties(2, 8000),
                HYBRID_PROPERTIES
            );

        HybridRetrievedChunk first =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                0,
                "Pierwszy fragment",
                0,
                17,
                0.95
            );

        HybridRetrievedChunk second =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                1,
                "Drugi fragment",
                100,
                114,
                0.85
            );

        HybridRetrievedChunk third =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                2,
                "Trzeci fragment",
                200,
                215,
                0.75
            );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(
            List.of(first, second, third)
        );

        // when
        List<RetrievedDocumentChunk> results =
            limitedService.retrieveRelevantChunks(
                documentId,
                ownerId,
                "test"
            );

        // then
        assertThat(results)
            .hasSize(2)
            .extracting(
                RetrievedDocumentChunk::chunkIndex
            )
            .containsExactly(0, 1);

        ArgumentCaptor<ChunkRetrievalCriteria>
            criteriaCaptor =
            ArgumentCaptor.forClass(
                ChunkRetrievalCriteria.class
            );

        verify(hybridRetrievalService)
            .retrieve(criteriaCaptor.capture());

        /*
         * 2 finalne chunki × multiplier 3
         * = 6 kandydatów.
         */
        assertThat(
            criteriaCaptor
                .getValue()
                .retrievedChunkLimit()
        ).isEqualTo(6);
    }

    @Test
    void shouldLimitChunksByContextCharacters() {
        // given
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        DocumentChunkRetrievalServiceImpl smallContextService =
            new DocumentChunkRetrievalServiceImpl(
                hybridRetrievalService,
                new AiChatProperties(5, 20),
                HYBRID_PROPERTIES
            );

        HybridRetrievedChunk first =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                0,
                "1234567890",
                0,
                10,
                0.90
            );

        HybridRetrievedChunk second =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                1,
                "abcdefghij",
                10,
                20,
                0.80
            );

        HybridRetrievedChunk third =
            hybridChunk(
                documentId,
                UUID.randomUUID(),
                2,
                "TOO_MUCH_TEXT",
                20,
                33,
                0.70
            );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(
            List.of(first, second, third)
        );

        // when
        List<RetrievedDocumentChunk> results =
            smallContextService.retrieveRelevantChunks(
                documentId,
                ownerId,
                "test"
            );

        // then
        assertThat(results)
            .hasSize(2)
            .extracting(
                RetrievedDocumentChunk::chunkIndex
            )
            .containsExactly(0, 1);

        assertThat(
            results
                .stream()
                .mapToInt(chunk ->
                    chunk.text().length()
                )
                .sum()
        ).isEqualTo(20);
    }

    @Test
    void shouldRejectChunkOutsideRequestedDocumentScope() {
        // given
        UUID requestedDocumentId =
            UUID.randomUUID();

        UUID unexpectedDocumentId =
            UUID.randomUUID();

        UUID ownerId = UUID.randomUUID();

        HybridRetrievedChunk unexpectedChunk =
            hybridChunk(
                unexpectedDocumentId,
                UUID.randomUUID(),
                0,
                "Fragment innego dokumentu",
                0,
                25,
                0.90
            );

        when(
            hybridRetrievalService.retrieve(
                any(ChunkRetrievalCriteria.class)
            )
        ).thenReturn(List.of(unexpectedChunk));

        // when / then
        assertThatThrownBy(() ->
            service.retrieveRelevantChunks(
                requestedDocumentId,
                ownerId,
                "test"
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Hybrid retrieval returned a chunk "
                    + "outside the requested document scope"
            );
    }

    @Test
    void shouldRejectBlankQuestionBeforeCallingRetrieval() {
        // when / then
        assertThatThrownBy(() ->
            service.retrieveRelevantChunks(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " "
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Question must not be blank");

        verifyNoInteractions(
            hybridRetrievalService
        );
    }

    private HybridRetrievedChunk hybridChunk(
        UUID documentId,
        UUID chunkId,
        int chunkIndex,
        String text,
        int startCharIndex,
        int endCharIndex,
        double score
    ) {
        return new HybridRetrievedChunk(
            documentId,
            "Decyzja o zasiłku",
            DOCUMENT_TYPE_ID,
            "Dokument urzędowy",
            chunkId,
            chunkIndex,
            text,
            startCharIndex,
            endCharIndex,
            score,
            score,
            0.0,
            HybridMatchType.SEMANTIC_ONLY,
            DOCUMENT_CREATED_AT
        );
    }
}

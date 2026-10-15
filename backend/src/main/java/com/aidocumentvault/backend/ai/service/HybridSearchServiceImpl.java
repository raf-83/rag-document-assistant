package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.dto.HybridSearchChunkResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchRequest;
import com.aidocumentvault.backend.ai.dto.HybridSearchResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchResultResponse;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.hybrid.HybridRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HybridSearchServiceImpl
    implements HybridSearchService {

    private static final int TEXT_PREVIEW_MAX_LENGTH = 500;

    private final CurrentUserProvider currentUserProvider;
    private final HybridRetrievalService hybridRetrievalService;
    private final AiHybridSearchProperties hybridSearchProperties;

    @Override
    public HybridSearchResponse search(
        HybridSearchRequest request
    ) {
        Objects.requireNonNull(
            request,
            "Hybrid search request must not be null"
        );

        int finalDocumentLimit =
            request.effectiveLimit();

        int retrievedChunkLimit =
            hybridSearchProperties.candidateLimitFor(
                finalDocumentLimit
            );

        UUID currentUserId =
            currentUserProvider.getCurrentUserId();

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                request.normalizedQuery(),
                currentUserId,
                request.getDocumentTypeId(),
                request.getCreatedFrom(),
                request.getCreatedTo(),
                request.effectiveScopeDocumentIds(),
                retrievedChunkLimit
            );

        List<HybridRetrievedChunk> retrievedChunks =
            Objects.requireNonNull(
                hybridRetrievalService.retrieve(criteria),
                "Hybrid retrieval results must not be null"
            );

        List<HybridSearchResultResponse> results =
            rankDocuments(retrievedChunks)
                .stream()
                .limit(finalDocumentLimit)
                .map(this::toDocumentResponse)
                .toList();

        return new HybridSearchResponse(
            finalDocumentLimit,
            results.size(),
            results
        );
    }

    /**
     * Grupuje znalezione chunki według dokumentu,
     * a następnie buduje ranking całych dokumentów.
     */
    private List<ScoredDocument> rankDocuments(
        List<HybridRetrievedChunk> retrievedChunks
    ) {
        Map<UUID, List<HybridRetrievedChunk>>
            chunksByDocumentId = new HashMap<>();

        for (HybridRetrievedChunk chunk : retrievedChunks) {
            chunksByDocumentId
                .computeIfAbsent(
                    chunk.documentId(),
                    ignored -> new ArrayList<>()
                )
                .add(chunk);
        }

        return chunksByDocumentId
            .values()
            .stream()
            .map(this::scoreDocument)
            .sorted(scoredDocumentComparator())
            .toList();
    }

    private ScoredDocument scoreDocument(
        List<HybridRetrievedChunk> documentChunks
    ) {
        if (documentChunks.isEmpty()) {
            throw new IllegalStateException(
                "Cannot score a document without chunks"
            );
        }

        /*
         * HybridRetrievalService zwraca już wyniki
         * od najlepszego do najsłabszego.
         *
         * Dodatkowe stabilne sortowanie po score zabezpiecza
         * tę klasę przed przypadkowo nieuporządkowaną listą.
         * Przy równych score zachowana zostaje wcześniejsza
         * kolejność ustalona przez HybridRetrievalService.
         */
        List<HybridRetrievedChunk> sortedChunks =
            documentChunks
                .stream()
                .sorted(
                    Comparator
                        .comparingDouble(
                            HybridRetrievedChunk::score
                        )
                        .reversed()
                )
                .toList();

        HybridRetrievedChunk bestChunk =
            sortedChunks.getFirst();

        verifyDocumentMetadata(
            sortedChunks,
            bestChunk
        );

        double documentScore =
            calculateDocumentScore(sortedChunks);

        List<HybridRetrievedChunk> returnedChunks =
            sortedChunks
                .stream()
                .limit(
                    hybridSearchProperties
                        .maxChunksPerDocument()
                )
                .toList();

        return new ScoredDocument(
            bestChunk,
            documentScore,
            bestChunk.score(),
            sortedChunks.size(),
            returnedChunks
        );
    }

    /**
     * Wynik dokumentu opiera się przede wszystkim
     * na jego najlepszym chunku.
     *
     * Dodatkowe pasujące chunki mogą zwiększyć wynik,
     * ale bonus jest kontrolowany przez:
     * maxDocumentBonusShare.
     */
    private double calculateDocumentScore(
        List<HybridRetrievedChunk> sortedChunks
    ) {
        double bestChunkScore =
            sortedChunks.getFirst().score();

        if (!Double.isFinite(bestChunkScore)
            || bestChunkScore <= 0.0) {

            throw new IllegalStateException(
                "Best chunk score must be finite "
                    + "and greater than 0"
            );
        }

        double relativeEvidence =
            sortedChunks
                .stream()
                .skip(1)
                .mapToDouble(chunk ->
                    Math.min(
                        chunk.score() / bestChunkScore,
                        1.0
                    )
                )
                .sum();

        double evidenceFactor =
            relativeEvidence / (1.0 + relativeEvidence);

        double remainingScoreSpace =
            1.0 - bestChunkScore;

        double documentBonus =
            remainingScoreSpace
                * hybridSearchProperties
                .maxDocumentBonusShare()
                * evidenceFactor;

        return Math.clamp(
            bestChunkScore + documentBonus,
            0.0,
            1.0
        );
    }

    /**
     * Wszystkie chunki zgrupowane pod jednym documentId
     * muszą opisywać dokładnie ten sam dokument.
     */
    private void verifyDocumentMetadata(
        List<HybridRetrievedChunk> documentChunks,
        HybridRetrievedChunk expected
    ) {
        for (HybridRetrievedChunk actual : documentChunks) {
            boolean metadataMatches =
                Objects.equals(
                    expected.documentId(),
                    actual.documentId()
                )
                    && Objects.equals(
                    expected.documentTitle(),
                    actual.documentTitle()
                )
                    && Objects.equals(
                    expected.documentTypeId(),
                    actual.documentTypeId()
                )
                    && Objects.equals(
                    expected.documentTypeName(),
                    actual.documentTypeName()
                )
                    && Objects.equals(
                    expected.documentCreatedAt(),
                    actual.documentCreatedAt()
                );

            if (!metadataMatches) {
                throw new IllegalStateException(
                    "Hybrid search returned inconsistent "
                        + "metadata for the same document"
                );
            }
        }
    }

    private Comparator<ScoredDocument>
    scoredDocumentComparator() {

        return Comparator
            .comparingDouble(ScoredDocument::score)
            .reversed()
            .thenComparing(
                Comparator
                    .comparingDouble(
                        ScoredDocument::bestChunkScore
                    )
                    .reversed()
            )
            .thenComparing(
                Comparator
                    .comparingInt(
                        ScoredDocument::matchedChunkCount
                    )
                    .reversed()
            )
            .thenComparing(scoredDocument ->
                scoredDocument
                    .representativeChunk()
                    .documentId()
                    .toString()
            );
    }

    private HybridSearchResultResponse toDocumentResponse(
        ScoredDocument scoredDocument
    ) {
        HybridRetrievedChunk document =
            scoredDocument.representativeChunk();

        List<HybridSearchChunkResponse> chunks =
            scoredDocument
                .chunks()
                .stream()
                .map(this::toChunkResponse)
                .toList();

        return new HybridSearchResultResponse(
            document.documentId(),
            document.documentTitle(),
            document.documentTypeId(),
            document.documentTypeName(),
            scoredDocument.score(),
            scoredDocument.bestChunkScore(),
            scoredDocument.matchedChunkCount(),
            chunks,
            document.documentCreatedAt()
        );
    }

    private HybridSearchChunkResponse toChunkResponse(
        HybridRetrievedChunk chunk
    ) {
        return new HybridSearchChunkResponse(
            chunk.chunkId(),
            chunk.chunkIndex(),
            chunk.score(),
            chunk.semanticScore(),
            chunk.keywordScore(),
            chunk.matchType(),
            createTextPreview(chunk.text())
        );
    }

    private String createTextPreview(String text) {
        if (text == null) {
            throw new IllegalStateException(
                "Hybrid retrieval returned a null chunk text"
            );
        }

        return text.substring(
            0,
            Math.min(
                text.length(),
                TEXT_PREVIEW_MAX_LENGTH
            )
        );
    }

    private record ScoredDocument(
        HybridRetrievedChunk representativeChunk,
        double score,
        double bestChunkScore,
        int matchedChunkCount,
        List<HybridRetrievedChunk> chunks
    ) {

        private ScoredDocument {
            Objects.requireNonNull(
                representativeChunk,
                "Representative chunk must not be null"
            );

            if (!Double.isFinite(score)
                || score < 0.0
                || score > 1.0) {

                throw new IllegalArgumentException(
                    "Document score must be between 0 and 1"
                );
            }

            if (!Double.isFinite(bestChunkScore)
                || bestChunkScore < 0.0
                || bestChunkScore > 1.0) {

                throw new IllegalArgumentException(
                    "Best chunk score must be between 0 and 1"
                );
            }

            if (matchedChunkCount < 1) {
                throw new IllegalArgumentException(
                    "Matched chunk count must be at least 1"
                );
            }

            chunks = List.copyOf(chunks);
        }
    }
}

package com.aidocumentvault.backend.ai.retrieval.hybrid;

import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.keyword.KeywordChunkRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.KeywordRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.semantic.SemanticChunkRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HybridRetrievalServiceImpl
    implements HybridRetrievalService {

    private final SemanticChunkRetrievalService
        semanticChunkRetrievalService;

    private final KeywordChunkRetrievalService
        keywordChunkRetrievalService;

    private final AiHybridSearchProperties
        hybridSearchProperties;

    @Override
    public List<HybridRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    ) {
        Objects.requireNonNull(
            criteria,
            "Hybrid retrieval criteria must not be null"
        );

        List<SemanticRetrievedChunk> semanticResults =
            Objects.requireNonNull(
                semanticChunkRetrievalService.retrieve(criteria),
                "Semantic retrieval results must not be null"
            );

        List<KeywordRetrievedChunk> keywordResults =
            Objects.requireNonNull(
                keywordChunkRetrievalService.retrieve(criteria),
                "Keyword retrieval results must not be null"
            );

        Map<UUID, CandidateAccumulator> candidatesByChunkId =
            new HashMap<>();

        addSemanticCandidates(
            candidatesByChunkId,
            semanticResults
        );

        addKeywordCandidates(
            candidatesByChunkId,
            keywordResults
        );

        return candidatesByChunkId
            .values()
            .stream()
            .map(candidate ->
                new ScoredCandidate(
                    candidate,
                    calculateNormalizedRrfScore(candidate)
                )
            )
            .sorted(scoredCandidateComparator())
            .map(this::toRetrievedChunk)
            .toList();
    }

    private void addSemanticCandidates(
        Map<UUID, CandidateAccumulator> candidatesByChunkId,
        List<SemanticRetrievedChunk> semanticResults
    ) {
        for (int index = 0;
             index < semanticResults.size();
             index++) {

            SemanticRetrievedChunk result =
                semanticResults.get(index);

            int semanticRank = index + 1;

            CandidateAccumulator incomingCandidate =
                CandidateAccumulator.fromSemantic(result);

            CandidateAccumulator candidate =
                candidatesByChunkId.computeIfAbsent(
                    result.chunkId(),
                    ignored -> incomingCandidate
                );

            candidate.verifyMetadata(incomingCandidate);

            candidate.addSemanticResult(
                semanticRank,
                requireFiniteScore(
                    result.score(),
                    "semantic"
                )
            );
        }
    }

    private void addKeywordCandidates(
        Map<UUID, CandidateAccumulator> candidatesByChunkId,
        List<KeywordRetrievedChunk> keywordResults
    ) {
        for (int index = 0;
             index < keywordResults.size();
             index++) {

            KeywordRetrievedChunk result =
                keywordResults.get(index);

            int keywordRank = index + 1;

            CandidateAccumulator incomingCandidate =
                CandidateAccumulator.fromKeyword(result);

            CandidateAccumulator candidate =
                candidatesByChunkId.computeIfAbsent(
                    result.chunkId(),
                    ignored -> incomingCandidate
                );

            candidate.verifyMetadata(incomingCandidate);

            candidate.addKeywordResult(
                keywordRank,
                requireFiniteScore(
                    result.score(),
                    "keyword"
                )
            );
        }
    }

    private double calculateNormalizedRrfScore(
        CandidateAccumulator candidate
    ) {
        double rawRrfScore = 0.0;
        int rrfK = hybridSearchProperties.rrfK();

        if (candidate.semanticRank != null) {
            rawRrfScore +=
                hybridSearchProperties.semanticWeight()
                    / (rrfK + candidate.semanticRank);
        }

        if (candidate.keywordRank != null) {
            rawRrfScore +=
                hybridSearchProperties.keywordWeight()
                    / (rrfK + candidate.keywordRank);
        }

        double weightSum =
            hybridSearchProperties.semanticWeight()
                + hybridSearchProperties.keywordWeight();

        double maximumPossibleRrfScore =
            weightSum / (rrfK + 1.0);

        if (!Double.isFinite(maximumPossibleRrfScore)
            || maximumPossibleRrfScore <= 0.0) {

            throw new IllegalStateException(
                "Maximum possible RRF score must be "
                    + "finite and greater than 0"
            );
        }

        double normalizedScore =
            rawRrfScore / maximumPossibleRrfScore;

        return Math.clamp(
            normalizedScore,
            0.0,
            1.0
        );
    }

    private Comparator<ScoredCandidate>
    scoredCandidateComparator() {

        return Comparator
            .comparingDouble(ScoredCandidate::score)
            .reversed()
            .thenComparingInt(
                scoredCandidate ->
                    scoredCandidate
                        .candidate()
                        .bestRank()
            )
            .thenComparing(
                scoredCandidate ->
                    scoredCandidate
                        .candidate()
                        .chunkId
                        .toString()
            );
    }

    private HybridRetrievedChunk toRetrievedChunk(
        ScoredCandidate scoredCandidate
    ) {
        CandidateAccumulator candidate =
            scoredCandidate.candidate();

        return new HybridRetrievedChunk(
            candidate.documentId,
            candidate.documentTitle,
            candidate.documentTypeId,
            candidate.documentTypeName,
            candidate.chunkId,
            candidate.chunkIndex,
            candidate.text,
            candidate.startCharIndex,
            candidate.endCharIndex,
            scoredCandidate.score(),
            candidate.semanticScoreOrZero(),
            candidate.keywordScoreOrZero(),
            candidate.matchType(),
            candidate.documentCreatedAt
        );
    }

    private double requireFiniteScore(
        double score,
        String scoreType
    ) {
        if (!Double.isFinite(score)) {
            throw new IllegalStateException(
                "%s retrieval returned an invalid score"
                    .formatted(scoreType)
            );
        }

        return score;
    }

    private record ScoredCandidate(
        CandidateAccumulator candidate,
        double score
    ) {
    }

    private static final class CandidateAccumulator {

        private final UUID documentId;
        private final String documentTitle;
        private final UUID documentTypeId;
        private final String documentTypeName;

        private final UUID chunkId;
        private final int chunkIndex;
        private final String text;
        private final int startCharIndex;
        private final int endCharIndex;

        private final Instant documentCreatedAt;

        private Integer semanticRank;
        private Integer keywordRank;

        private Double semanticScore;
        private Double keywordScore;

        private CandidateAccumulator(
            UUID documentId,
            String documentTitle,
            UUID documentTypeId,
            String documentTypeName,
            UUID chunkId,
            int chunkIndex,
            String text,
            int startCharIndex,
            int endCharIndex,
            Instant documentCreatedAt
        ) {
            this.documentId = documentId;
            this.documentTitle = documentTitle;
            this.documentTypeId = documentTypeId;
            this.documentTypeName = documentTypeName;
            this.chunkId = chunkId;
            this.chunkIndex = chunkIndex;
            this.text = text;
            this.startCharIndex = startCharIndex;
            this.endCharIndex = endCharIndex;
            this.documentCreatedAt = documentCreatedAt;
        }

        private static CandidateAccumulator fromSemantic(
            SemanticRetrievedChunk result
        ) {
            return new CandidateAccumulator(
                result.documentId(),
                result.documentTitle(),
                result.documentTypeId(),
                result.documentTypeName(),
                result.chunkId(),
                result.chunkIndex(),
                result.text(),
                result.startCharIndex(),
                result.endCharIndex(),
                result.documentCreatedAt()
            );
        }

        private static CandidateAccumulator fromKeyword(
            KeywordRetrievedChunk result
        ) {
            return new CandidateAccumulator(
                result.documentId(),
                result.documentTitle(),
                result.documentTypeId(),
                result.documentTypeName(),
                result.chunkId(),
                result.chunkIndex(),
                result.text(),
                result.startCharIndex(),
                result.endCharIndex(),
                result.documentCreatedAt()
            );
        }

        private void verifyMetadata(
            CandidateAccumulator incomingCandidate
        ) {
            boolean metadataMatches =
                Objects.equals(
                    documentId,
                    incomingCandidate.documentId
                )
                    && Objects.equals(
                    documentTitle,
                    incomingCandidate.documentTitle
                )
                    && Objects.equals(
                    documentTypeId,
                    incomingCandidate.documentTypeId
                )
                    && Objects.equals(
                    documentTypeName,
                    incomingCandidate.documentTypeName
                )
                    && Objects.equals(
                    chunkId,
                    incomingCandidate.chunkId
                )
                    && chunkIndex
                        == incomingCandidate.chunkIndex
                    && Objects.equals(
                    text,
                    incomingCandidate.text
                )
                    && startCharIndex
                        == incomingCandidate.startCharIndex
                    && endCharIndex
                        == incomingCandidate.endCharIndex
                    && Objects.equals(
                    documentCreatedAt,
                    incomingCandidate.documentCreatedAt
                );

            if (!metadataMatches) {
                throw new IllegalStateException(
                    "Hybrid retrieval returned inconsistent "
                        + "metadata for the same chunk"
                );
            }
        }

        private void addSemanticResult(
            int rank,
            double score
        ) {
            if (semanticRank == null || rank < semanticRank) {
                semanticRank = rank;
                semanticScore = score;
            }
        }

        private void addKeywordResult(
            int rank,
            double score
        ) {
            if (keywordRank == null || rank < keywordRank) {
                keywordRank = rank;
                keywordScore = score;
            }
        }

        private int bestRank() {
            if (semanticRank == null
                && keywordRank == null) {

                throw new IllegalStateException(
                    "Hybrid candidate has no retrieval rank"
                );
            }

            if (semanticRank == null) {
                return keywordRank;
            }

            if (keywordRank == null) {
                return semanticRank;
            }

            return Math.min(
                semanticRank,
                keywordRank
            );
        }

        private double semanticScoreOrZero() {
            return semanticScore == null
                ? 0.0
                : semanticScore;
        }

        private double keywordScoreOrZero() {
            return keywordScore == null
                ? 0.0
                : keywordScore;
        }

        private HybridMatchType matchType() {
            if (semanticRank != null
                && keywordRank != null) {

                return HybridMatchType.HYBRID;
            }

            if (semanticRank != null) {
                return HybridMatchType.SEMANTIC_ONLY;
            }

            if (keywordRank != null) {
                return HybridMatchType.KEYWORD_ONLY;
            }

            throw new IllegalStateException(
                "Hybrid candidate has no match type"
            );
        }
    }
}

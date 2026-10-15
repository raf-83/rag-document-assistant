package com.aidocumentvault.backend.ai.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai.hybrid-search")
public record AiHybridSearchProperties(

    @Min(value = 1, message = "RRF k must be at least 1")
    @Max(value = 1000, message = "RRF k must not exceed 1000")
    int rrfK,

    @DecimalMin(
        value = "0.0",
        inclusive = false,
        message = "Semantic weight must be greater than 0"
    )
    @DecimalMax(
        value = "1.0",
        message = "Semantic weight must not exceed 1"
    )
    double semanticWeight,

    @DecimalMin(
        value = "0.0",
        inclusive = false,
        message = "Keyword weight must be greater than 0"
    )
    @DecimalMax(
        value = "1.0",
        message = "Keyword weight must not exceed 1"
    )
    double keywordWeight,

    @Min(
        value = 1,
        message = "Candidate multiplier must be at least 1"
    )
    @Max(
        value = 20,
        message = "Candidate multiplier must not exceed 20"
    )
    int candidateMultiplier,

    @Min(
        value = 1,
        message = "Max candidate limit must be at least 1"
    )
    @Max(
        value = 1000,
        message = "Max candidate limit must not exceed 1000"
    )
    int maxCandidateLimit,

    @Min(
        value = 1,
        message = "Max chunks per document must be at least 1"
    )
    @Max(
        value = 20,
        message = "Max chunks per document must not exceed 20"
    )
    int maxChunksPerDocument,

    @DecimalMin(
        value = "0.0",
        message = "Max document bonus share must not be negative"
    )
    @DecimalMax(
        value = "1.0",
        message = "Max document bonus share must not exceed 1"
    )
    double maxDocumentBonusShare
) {

    private static final double WEIGHT_SUM_TOLERANCE = 0.000001;

    public int candidateLimitFor(int finalDocumentLimit) {
        if (finalDocumentLimit < 1) {
            throw new IllegalArgumentException(
                "Final document limit must be at least 1"
            );
        }

        long candidateLimit =
            (long) finalDocumentLimit
                * candidateMultiplier
                * maxChunksPerDocument;

        return (int) Math.min(
            candidateLimit,
            maxCandidateLimit
        );
    }

    public int candidateLimitForChunks(int finalChunkLimit) {
        if (finalChunkLimit < 1) {
            throw new IllegalArgumentException(
                "Final chunk limit must be at least 1"
            );
        }

        long candidateLimit =
            (long) finalChunkLimit
                * candidateMultiplier;

        return (int) Math.min(
            candidateLimit,
            maxCandidateLimit
        );
    }

    @AssertTrue(message = "Hybrid search weights must sum to 1.0")
    public boolean isWeightSumValid() {
        double weightSum = semanticWeight + keywordWeight;

        return Math.abs(weightSum - 1.0)
            < WEIGHT_SUM_TOLERANCE;
    }
}

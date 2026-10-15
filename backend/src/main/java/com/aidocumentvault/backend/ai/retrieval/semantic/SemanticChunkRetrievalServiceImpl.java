package com.aidocumentvault.backend.ai.retrieval.semantic;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.EmbeddingClient;
import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.ai.search.persistence.SemanticSearchRepository;
import com.aidocumentvault.backend.ai.search.persistence.SemanticSearchResultRow;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemanticChunkRetrievalServiceImpl
    implements SemanticChunkRetrievalService {

    private static final UUID DUMMY_SCOPE_DOCUMENT_ID =
        UUID.fromString(
            "00000000-0000-0000-0000-000000000000"
        );

    private final EmbeddingClient embeddingClient;
    private final AiEmbeddingProperties aiEmbeddingProperties;
    private final SemanticSearchRepository semanticSearchRepository;

    @Override
    public List<SemanticRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    ) {
        Objects.requireNonNull(
            criteria,
            "Hybrid retrieval criteria must not be null"
        );

        EmbeddingVector queryEmbedding =
            generateQueryEmbedding(criteria.query());

        String pgVectorLiteral =
            toPgVectorLiteral(queryEmbedding.values());

        Set<UUID> scopeDocumentIds =
            criteria.scopeDocumentIds();

        boolean scopeDocumentIdsEmpty =
            scopeDocumentIds.isEmpty();

        Collection<UUID> scopeDocumentIdsParameter =
            scopeDocumentIdsEmpty
                ? List.of(DUMMY_SCOPE_DOCUMENT_ID)
                : scopeDocumentIds;

        return semanticSearchRepository
            .findNearestChunks(
                pgVectorLiteral,
                criteria.ownerId(),
                aiEmbeddingProperties.model(),
                aiEmbeddingProperties.dimensions(),
                criteria.documentTypeId(),
                criteria.createdFrom(),
                criteria.createdTo(),
                scopeDocumentIdsEmpty,
                scopeDocumentIdsParameter,
                criteria.retrievedChunkLimit()
            )
            .stream()
            .map(this::toRetrievedChunk)
            .toList();
    }

    private EmbeddingVector generateQueryEmbedding(
        String query
    ) {
        List<EmbeddingVector> embeddings =
            embeddingClient.embed(List.of(query));

        if (embeddings == null || embeddings.size() != 1) {
            throw new IllegalStateException(
                "Embedding client must return exactly one "
                    + "query embedding"
            );
        }

        return embeddings.getFirst();
    }

    private String toPgVectorLiteral(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(
                "Query embedding must not be empty"
            );
        }

        if (values.length
            != aiEmbeddingProperties.dimensions()) {

            throw new IllegalArgumentException(
                "Query embedding dimensions do not match "
                    + "configured dimensions"
            );
        }

        StringBuilder builder = new StringBuilder("[");

        for (int index = 0; index < values.length; index++) {
            float value = values[index];

            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                    "Query embedding contains invalid value"
                );
            }

            if (index > 0) {
                builder.append(",");
            }

            builder.append(Float.toString(value));
        }

        builder.append("]");

        return builder.toString();
    }

    private SemanticRetrievedChunk toRetrievedChunk(
        SemanticSearchResultRow row
    ) {
        Integer chunkIndex = row.getChunkIndex();
        Integer startCharIndex = row.getStartCharIndex();
        Integer endCharIndex = row.getEndCharIndex();
        Double score = row.getScore();

        if (chunkIndex == null) {
            throw new IllegalStateException(
                "Semantic search returned a null chunk index"
            );
        }

        if (startCharIndex == null) {
            throw new IllegalStateException(
                "Semantic search returned a null start char index"
            );
        }

        if (endCharIndex == null) {
            throw new IllegalStateException(
                "Semantic search returned a null end char index"
            );
        }

        if (score == null || !Double.isFinite(score)) {
            throw new IllegalStateException(
                "Semantic search returned an invalid score"
            );
        }

        return new SemanticRetrievedChunk(
            row.getDocumentId(),
            row.getDocumentTitle(),
            row.getDocumentTypeId(),
            row.getDocumentTypeName(),
            row.getChunkId(),
            chunkIndex,
            row.getText(),
            startCharIndex,
            endCharIndex,
            score,
            row.getDocumentCreatedAt()
        );
    }
}

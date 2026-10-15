package com.aidocumentvault.backend.ai.retrieval.keyword;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.model.KeywordRetrievedChunk;
import com.aidocumentvault.backend.ai.search.persistence.KeywordSearchRepository;
import com.aidocumentvault.backend.ai.search.persistence.KeywordSearchResultRow;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeywordChunkRetrievalServiceImpl
    implements KeywordChunkRetrievalService {

    private static final UUID DUMMY_SCOPE_DOCUMENT_ID =
        UUID.fromString(
            "00000000-0000-0000-0000-000000000000"
        );

    private final KeywordSearchRepository keywordSearchRepository;

    @Override
    public List<KeywordRetrievedChunk> retrieve(
        ChunkRetrievalCriteria criteria
    ) {
        Objects.requireNonNull(
            criteria,
            "Hybrid retrieval criteria must not be null"
        );

        Set<UUID> scopeDocumentIds =
            criteria.scopeDocumentIds();

        boolean scopeDocumentIdsEmpty =
            scopeDocumentIds.isEmpty();

        Collection<UUID> scopeDocumentIdsParameter =
            scopeDocumentIdsEmpty
                ? List.of(DUMMY_SCOPE_DOCUMENT_ID)
                : scopeDocumentIds;

        return keywordSearchRepository
            .findKeywordChunks(
                criteria.query(),
                criteria.ownerId(),
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

    private KeywordRetrievedChunk toRetrievedChunk(
        KeywordSearchResultRow row
    ) {
        Objects.requireNonNull(
            row,
            "Keyword search result row must not be null"
        );

        Integer chunkIndex = row.getChunkIndex();
        Integer startCharIndex = row.getStartCharIndex();
        Integer endCharIndex = row.getEndCharIndex();
        Double score = row.getScore();

        if (chunkIndex == null) {
            throw new IllegalStateException(
                "Keyword search returned a null chunk index"
            );
        }

        if (startCharIndex == null) {
            throw new IllegalStateException(
                "Keyword search returned a null start char index"
            );
        }

        if (endCharIndex == null) {
            throw new IllegalStateException(
                "Keyword search returned a null end char index"
            );
        }

        if (score == null
            || !Double.isFinite(score)
            || score < 0.0) {

            throw new IllegalStateException(
                "Keyword search returned an invalid score"
            );
        }

        return new KeywordRetrievedChunk(
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

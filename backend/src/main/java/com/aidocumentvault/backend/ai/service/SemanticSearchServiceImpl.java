package com.aidocumentvault.backend.ai.service;

import com.aidocumentvault.backend.ai.dto.SemanticSearchRequest;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResponse;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResultResponse;
import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.semantic.SemanticChunkRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.SemanticRetrievedChunk;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private static final int TEXT_PREVIEW_MAX_LENGTH = 500;

    private final CurrentUserProvider currentUserProvider;
    private final SemanticChunkRetrievalService semanticChunkRetrievalService;

    @Override
    public SemanticSearchResponse search(
        SemanticSearchRequest request
    ) {
        UUID currentUserId =
            currentUserProvider.getCurrentUserId();

        int limit = request.effectiveLimit();

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                request.normalizedQuery(),
                currentUserId,
                request.getDocumentTypeId(),
                request.getCreatedFrom(),
                request.getCreatedTo(),
                request.effectiveScopeDocumentIds(),
                limit
            );

        List<SemanticSearchResultResponse> results =
            semanticChunkRetrievalService
                .retrieve(criteria)
                .stream()
                .map(this::toResponse)
                .toList();

        return new SemanticSearchResponse(
            limit,
            results.size(),
            results
        );
    }

    private SemanticSearchResultResponse toResponse(
        SemanticRetrievedChunk chunk
    ) {
        return new SemanticSearchResultResponse(
            chunk.documentId(),
            chunk.documentTitle(),
            chunk.documentTypeId(),
            chunk.documentTypeName(),
            chunk.chunkId(),
            chunk.chunkIndex(),
            chunk.score(),
            createTextPreview(chunk.text()),
            chunk.documentCreatedAt()
        );
    }

    private String createTextPreview(String text) {
        if (text == null) {
            throw new IllegalStateException(
                "Semantic retrieval returned a null chunk text"
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
}

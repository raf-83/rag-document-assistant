package com.aidocumentvault.backend.document.service;

import com.aidocumentvault.backend.document.dto.DocumentSearchPageResponse;
import com.aidocumentvault.backend.document.dto.DocumentSearchResultResponse;
import com.aidocumentvault.backend.document.entity.DocumentStatus;
import com.aidocumentvault.backend.document.exception.InvalidDocumentSearchQueryException;
import com.aidocumentvault.backend.document.repository.DocumentSearchProjection;
import com.aidocumentvault.backend.document.repository.DocumentSearchRepository;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentSearchServiceImpl implements DocumentSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final DocumentSearchRepository documentSearchRepository;

    private final CurrentUserProvider currentUserProvider;

    @Override
    public DocumentSearchPageResponse searchDocuments(
            String query,
            int page,
            int size
    ) {
        String normalizedQuery = normalizeQuery(query);

        validatePage(page);

        validateSize(size);

        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Pageable pageable = PageRequest.of(page, size);

        Page<DocumentSearchProjection> searchResult =
                documentSearchRepository.searchDocuments(
                        currentUserId,
                        normalizedQuery,
                        pageable
                );

        List<DocumentSearchResultResponse> results =
                searchResult.getContent()
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return new DocumentSearchPageResponse(
                results,
                searchResult.getNumber(),
                searchResult.getSize(),
                searchResult.getTotalElements(),
                searchResult.getTotalPages()
        );
    }

    private DocumentSearchResultResponse mapToResponse(
            DocumentSearchProjection projection
    ) {
        return new DocumentSearchResultResponse(
                projection.getDocumentId(),
                projection.getTitle(),
                projection.getDocumentType(),
                DocumentStatus.valueOf(projection.getStatus()),
                projection.getTitleSnippet(),
                projection.getContentSnippet(),
                Boolean.TRUE.equals(projection.getMatchedInTitle()),
                Boolean.TRUE.equals(projection.getMatchedInContent()),
                projection.getRank(),
                projection.getCreatedAt(),
                projection.getUpdatedAt()
        );
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            throw new InvalidDocumentSearchQueryException(
                    "Search query cannot be null."
            );
        }

        String normalizedQuery = query.trim();

        if (normalizedQuery.length() < 2) {
            throw new InvalidDocumentSearchQueryException(
                    "Search query must contain at least 2 characters."
            );
        }

        return normalizedQuery;
    }

    private void validatePage(int page) {
        if (page < 0) {
            throw new InvalidDocumentSearchQueryException(
                    "Page number cannot be negative."
            );
        }
    }

    private void validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidDocumentSearchQueryException(
                    "Page size must be between 1 and 50."
            );
        }
    }
}

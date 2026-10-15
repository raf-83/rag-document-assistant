package com.aidocumentvault.backend.ai.search.persistence;

import java.time.Instant;
import java.util.UUID;

public interface SemanticSearchResultRow {

    UUID getDocumentId();

    String getDocumentTitle();

    UUID getDocumentTypeId();

    String getDocumentTypeName();

    UUID getChunkId();

    Integer getChunkIndex();

    Double getScore();

    String getText();

    Integer getStartCharIndex();

    Integer getEndCharIndex();

    Instant getDocumentCreatedAt();
}

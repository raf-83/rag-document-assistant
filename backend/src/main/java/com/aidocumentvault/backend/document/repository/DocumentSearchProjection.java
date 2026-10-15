package com.aidocumentvault.backend.document.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface DocumentSearchProjection {

    UUID getDocumentId();

    String getTitle();

    String getDocumentType();

    String getStatus();

    String getTitleSnippet();

    String getContentSnippet();

    Boolean getMatchedInTitle();

    Boolean getMatchedInContent();

    BigDecimal getRank();

    Instant  getCreatedAt();

    Instant getUpdatedAt();
}

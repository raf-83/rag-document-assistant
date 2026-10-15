package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.DocumentSummary;
import com.aidocumentvault.backend.document.entity.SummaryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, UUID> {

    List<DocumentSummary> findAllByDocumentId(UUID documentId);

    List<DocumentSummary> findAllByDocumentIdAndSummaryType(UUID documentId, SummaryType summaryType);
}
